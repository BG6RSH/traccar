/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class HuabaoFrameEncoder extends MessageToByteEncoder<ByteBuf> {

        /**
     * 编码方法，将输入的字节缓冲区进行特殊编码处理
     *
     * @param ctx ChannelHandlerContext对象，用于处理通道上下文
     * @param msg 输入的字节缓冲区，包含待编码的数据
     * @param out 输出的字节缓冲区，用于存储编码后的数据
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {

        // 判断是否使用替代编码方案：检查消息起始字节是否为0xe7
        boolean alternative = msg.getUnsignedByte(msg.readerIndex()) == 0xe7;

        int startIndex = msg.readerIndex();
        // 遍历输入缓冲区中的所有可读字节
        while (msg.isReadable()) {
            int index = msg.readerIndex();
            int b = msg.readUnsignedByte();

            // 处理替代编码方案中的特殊字节转义
            if (alternative && (b == 0xe6 || b == 0x3d || b == 0x3e)) {
                out.writeByte(b == 0xe6 ? 0xe6 : 0x3e);
                out.writeByte(b == 0x3d ? 0x02 : 0x01);
            // 处理替代编码方案中0xe7字节的转义（除起始位置外）
            } else if (alternative && b == 0xe7 && index != startIndex && msg.isReadable()) {
                out.writeByte(0xe6);
                out.writeByte(0x02);
            // 处理标准编码方案中0x7d字节的转义
            } else if (!alternative && b == 0x7d) {
                out.writeByte(0x7d);
                out.writeByte(0x01);
            // 处理标准编码方案中0x7e字节的转义（除起始位置外）
            } else if (!alternative && b == 0x7e && index != startIndex && msg.isReadable()) {
                out.writeByte(0x7d);
                out.writeByte(0x02);
            // 写入普通字节，无需转义
            } else {
                out.writeByte(b);
            }
        }
    }

}
