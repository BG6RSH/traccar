/*
 * Copyright 2017 - 2025 Anton Tananaev (anton@traccar.org)
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
import org.traccar.BaseProtocolEncoder;
import org.traccar.Protocol;
import org.traccar.config.Keys;
import org.traccar.helper.DataConverter;
import org.traccar.helper.model.AttributeUtil;
import org.traccar.model.Command;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * 华宝协议编码器，用于将通用命令转换为华宝设备可识别的协议格式。
 */
public class HuabaoProtocolEncoder extends BaseProtocolEncoder {

    /**
     * 构造函数，初始化协议编码器。
     *
     * @param protocol 协议实例
     */
    public HuabaoProtocolEncoder(Protocol protocol) {
        super(protocol);
    }

    /**
     * 将传入的通用命令编码为华宝协议格式的数据包。
     *
     * @param command 要编码的命令对象
     * @return 编码后的数据包对象，若不支持该命令类型则返回 null
     */
    @Override
    protected Object encodeCommand(Command command) {

        // 判断是否使用替代协议格式
        boolean alternative = AttributeUtil.lookup(
                getCacheManager(), Keys.PROTOCOL_ALTERNATIVE.withPrefix(getProtocolName()), command.getDeviceId());

        // 获取设备唯一标识并转换为字节缓冲区
        ByteBuf id = Unpooled.wrappedBuffer(
                DataConverter.parseHex(getUniqueId(command.getDeviceId())));
        try {
            // 创建用于构建命令数据的缓冲区
            ByteBuf data = Unpooled.buffer();
            // 获取当前时间并格式化为华宝协议所需格式
            byte[] time = DataConverter.parseHex(new SimpleDateFormat("yyMMddHHmmss").format(new Date()));

            switch (command.getType()) {
                // 自定义命令处理逻辑
                case Command.TYPE_CUSTOM:
                    String model = getDeviceModel(command.getDeviceId());
                    if (model != null && Set.of("AL300", "GL100", "VL300").contains(model)) {
                        // 处理支持 AT 命令透传的设备型号
                        data.writeByte(1); // 参数数量
                        data.writeInt(0xF030); // AT 命令透传参数 ID
                        int length = command.getString(Command.KEY_DATA).length();
                        data.writeByte(length); // 数据长度
                        data.writeCharSequence(command.getString(Command.KEY_DATA), StandardCharsets.US_ASCII); // 写入数据
                        return HuabaoProtocolDecoder.formatMessage(
                                0x7e, HuabaoProtocolDecoder.MSG_CONFIGURATION_PARAMETERS, id, false, data);
                    } else if ("BSJ".equals(model)) {
                        // 处理 BSJ 型号设备
                        data.writeByte(1); // 标志位
                        var charset = Charset.isSupported("GBK") ? Charset.forName("GBK") : StandardCharsets.US_ASCII;
                        data.writeCharSequence(command.getString(Command.KEY_DATA), charset); // 写入数据
                        return HuabaoProtocolDecoder.formatMessage(
                                0x7e, HuabaoProtocolDecoder.MSG_SEND_TEXT_MESSAGE, id, false, data);
                    } else {
                        // 默认处理方式：直接解析为十六进制数据
                        return Unpooled.wrappedBuffer(DataConverter.parseHex(command.getString(Command.KEY_DATA)));
                    }
                // 设备重启命令
                case Command.TYPE_REBOOT_DEVICE:
                    data.writeByte(1); // 参数数量
                    data.writeByte(0x23); // 参数 ID
                    data.writeByte(1); // 参数值长度
                    data.writeByte(0x03); // 重启指令
                    return HuabaoProtocolDecoder.formatMessage(
                            0x7e, HuabaoProtocolDecoder.MSG_PARAMETER_SETTING, id, false, data);
                // 设置定位上报频率命令
                case Command.TYPE_POSITION_PERIODIC:
                    data.writeByte(1); // 参数数量
                    data.writeByte(0x06); // 参数 ID
                    data.writeByte(4); // 参数值长度
                    data.writeInt(command.getInteger(Command.KEY_FREQUENCY)); // 写入频率值
                    return HuabaoProtocolDecoder.formatMessage(
                            0x7e, HuabaoProtocolDecoder.MSG_PARAMETER_SETTING, id, false, data);
                // 布防/撤防命令
                case Command.TYPE_ALARM_ARM:
                case Command.TYPE_ALARM_DISARM:
                    data.writeByte(1); // 参数数量
                    data.writeByte(0x24); // 参数 ID
                    String username = "user";
                    data.writeByte(1 + username.length()); // 参数值长度
                    data.writeByte(command.getType().equals(Command.TYPE_ALARM_ARM) ? 0x01 : 0x00); // 指令值
                    data.writeCharSequence(username, StandardCharsets.US_ASCII); // 用户名
                    return HuabaoProtocolDecoder.formatMessage(
                            0x7e, HuabaoProtocolDecoder.MSG_PARAMETER_SETTING, id, false, data);
                // 发动机控制命令
                case Command.TYPE_ENGINE_STOP:
                case Command.TYPE_ENGINE_RESUME:
                    if (alternative) {
                        // 使用替代协议格式发送油控指令
                        data.writeByte(command.getType().equals(Command.TYPE_ENGINE_STOP) ? 0x01 : 0x00);
                        data.writeBytes(time); // 写入时间戳
                        return HuabaoProtocolDecoder.formatMessage(
                                0x7e, HuabaoProtocolDecoder.MSG_OIL_CONTROL, id, false, data);
                    } else {
                        // 使用标准终端控制指令
                        if ("VL300".equals(getDeviceModel(command.getDeviceId()))) {
                            data.writeCharSequence(command.getType().equals(Command.TYPE_ENGINE_STOP) ? "#0;1" : "#0;0",
                                    StandardCharsets.US_ASCII);
                        } else {
                            data.writeByte(command.getType().equals(Command.TYPE_ENGINE_STOP) ? 0xf0 : 0xf1);
                        }
                        return HuabaoProtocolDecoder.formatMessage(
                                0x7e, HuabaoProtocolDecoder.MSG_TERMINAL_CONTROL, id, false, data);
                    }
                default:
                    // 不支持的命令类型
                    return null;
            }
        } finally {
            // 释放设备 ID 缓冲区资源
            id.release();
        }
    }

}
