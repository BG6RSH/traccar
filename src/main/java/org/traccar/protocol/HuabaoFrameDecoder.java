/*
 * Copyright 2015 - 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.traccar.BaseFrameDecoder;

/**
 * 华宝协议帧解码器，用于从字节流中解析出完整的数据包。
 * 支持两种格式：以'('开头、')'结尾的文本协议；以及基于特定分隔符的二进制协议（支持转义）。
 */
public class HuabaoFrameDecoder extends BaseFrameDecoder {

    /**
     * 解码函数，根据输入的字节缓冲区提取一个完整数据包。
     *
     * @param ctx ChannelHandlerContext 上下文对象
     * @param channel 当前通道
     * @param buf 输入的字节缓冲区
     * @return 返回解析出的一个完整数据包 ByteBuf，若未找到完整包则返回 null
     * @throws Exception 解码过程中可能抛出异常
     */
    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ByteBuf buf) throws Exception {

        // 检查是否有足够的可读字节
        if (buf.readableBytes() < 2) {
            return null;
        }

        // 判断是否是文本协议格式（以'('开始）
        if (buf.getByte(buf.readerIndex()) == '(') {

            // 查找结束符 ')'
            int index = buf.indexOf(buf.readerIndex() + 1, buf.writerIndex(), (byte) ')');
            if (index >= 0) {
                // 截取整个消息包并返回
                return buf.readRetainedSlice(index + 1);
            }

        } else {

            // 获取首字节作为分隔符，并判断是否使用替代模式
            int delimiter = buf.getUnsignedByte(buf.readerIndex());
            boolean alternative = delimiter == 0xe7;

            // 在后续内容中查找对应的结束分隔符
            int index = buf.indexOf(buf.readerIndex() + 1, buf.writerIndex(), (byte) delimiter);
            if (index >= 0) {
                // 创建一个新的缓冲区来存储解码后的结果
                ByteBuf result = Unpooled.buffer(index + 1 - buf.readerIndex());

                // 遍历原始缓冲区直到结束位置
                while (buf.readerIndex() <= index) {
                    int b = buf.readUnsignedByte();

                    // 处理替代模式下的特殊转义规则
                    if (alternative && (b == 0xe6 || b == 0x3e)) {
                        int ext = buf.readUnsignedByte();
                        if (b == 0xe6 && ext == 0x01) {
                            result.writeByte(0xe6);
                        } else if (b == 0xe6 && ext == 0x02) {
                            result.writeByte(0xe7);
                        } else if (b == 0x3e && ext == 0x01) {
                            result.writeByte(0x3e);
                        } else if (b == 0x3e && ext == 0x02) {
                            result.writeByte(0x3d);
                        }
                    }
                    // 处理标准模式下的转义字符 0x7d
                    else if (!alternative && b == 0x7d) {
                        int ext = buf.readUnsignedByte();
                        if (ext == 0x01) {
                            result.writeByte(0x7d);
                        } else if (ext == 0x02) {
                            result.writeByte(0x7e);
                        }
                    }
                    // 其他普通字节直接写入结果
                    else {
                        result.writeByte(b);
                    }
                }

                return result;
            }

        }

        return null;
    }

}
