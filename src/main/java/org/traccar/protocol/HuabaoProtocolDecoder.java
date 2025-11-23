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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.BufferUtil;
import org.traccar.model.WifiAccessPoint;
import org.traccar.session.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BcdUtil;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class HuabaoProtocolDecoder extends BaseProtocolDecoder {

    public HuabaoProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    /** 终端通用应答消息 */
    public static final int MSG_TERMINAL_GENERAL_RESPONSE = 0x0001;

    /** 平台通用应答消息 */
    public static final int MSG_GENERAL_RESPONSE = 0x8001;

    /** 通用应答消息2 */
    public static final int MSG_GENERAL_RESPONSE_2 = 0x4401;

    /** 终端心跳数据消息体为空 */
    public static final int MSG_HEARTBEAT = 0x0002;

    /** 心跳消息2 */
    public static final int MSG_HEARTBEAT_2 = 0x0506;

    /** 终端注册消息 */
    public static final int MSG_TERMINAL_REGISTER = 0x0100;

    /** 终端注册应答消息 */
    public static final int MSG_TERMINAL_REGISTER_RESPONSE = 0x8100;

    /** 终端控制消息 */
    public static final int MSG_TERMINAL_CONTROL = 0x8105;

    /** 终端鉴权消息 */
    public static final int MSG_TERMINAL_AUTH = 0x0102;

    /** 位置上报消息 */
    public static final int MSG_LOCATION_REPORT = 0x0200;

    /** 位置批量上传消息2 */
    public static final int MSG_LOCATION_BATCH_2 = 0x0210;

    /** 加速度消息 */
    public static final int MSG_ACCELERATION = 0x2070;

    /** 位置上报消息2 */
    public static final int MSG_LOCATION_REPORT_2 = 0x5501;

    /** 盲点位置上报消息 */
    public static final int MSG_LOCATION_REPORT_BLIND = 0x5502;

    /** 位置批量上传消息 */
    public static final int MSG_LOCATION_BATCH = 0x0704;

    /** 油路控制消息 */
    public static final int MSG_OIL_CONTROL = 0xa006;

    /** 时间同步请求消息 */
    public static final int MSG_TIME_SYNC_REQUEST = 0x0109;

    /** 时间同步响应消息 */
    public static final int MSG_TIME_SYNC_RESPONSE = 0x8109;

    /** 照片消息 */
    public static final int MSG_PHOTO = 0x8888;

    /** 透明传输消息 */
    public static final int MSG_TRANSPARENT = 0x0900;

    /** 参数设置消息 */
    public static final int MSG_PARAMETER_SETTING = 0x0310;

    /** 发送文本消息 */
    public static final int MSG_SEND_TEXT_MESSAGE = 0x8300;

    /** 上报文本消息 */
    public static final int MSG_REPORT_TEXT_MESSAGE = 0x6006;

    /** 配置参数消息 */
    public static final int MSG_CONFIGURATION_PARAMETERS = 0x8103;

    /** 命令响应消息 */
    public static final int MSG_COMMAND_RESPONSE = 0x0701;

    /** 操作成功结果码 */
    public static final int RESULT_SUCCESS = 0;

    /** 协议分隔符 */
    private int delimiter = 0x7e;

    private static final Logger LOG = LoggerFactory.getLogger(HuabaoProtocolDecoder.class);

    public boolean isAlternative() {
        return delimiter == 0xe7;
    }

    /**
     * 格式化消息数据包
     *
     * @param delimiter 分隔符，用于标识消息的开始和结束
     * @param type 消息类型
     * @param id 消息ID数据
     * @param shortIndex 索引长度标志，true表示使用短索引(1字节)，false表示使用长索引(2字节)
     * @param data 消息体数据
     * @return 格式化后的完整消息数据包
     */
    public static ByteBuf formatMessage(int delimiter, int type, ByteBuf id, boolean shortIndex, ByteBuf data) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(delimiter);
        buf.writeShort(type);
        buf.writeShort(data.readableBytes());
        buf.writeBytes(id);
        // 写入索引长度标识
        if (shortIndex) {
            buf.writeByte(1);
        } else {
            buf.writeShort(0);
        }
        buf.writeBytes(data);
        data.release();
        // 计算并写入校验和
        buf.writeByte(Checksum.xor(buf.nioBuffer(1, buf.readableBytes() - 1)));
        buf.writeByte(delimiter);
        return buf;
    }


    /**
     * 发送通用响应消息
     *
     * @param channel 网络通道，用于发送响应数据
     * @param remoteAddress 远程地址，指定响应发送的目标地址
     * @param id 设备ID，用于标识响应的目标设备
     * @param type 响应类型，表示响应的消息类型
     * @param index 索引值，用于标识具体的响应序号
     */
    private void sendGeneralResponse(
            Channel channel, SocketAddress remoteAddress, ByteBuf id, int type, int index) {
        if (channel != null) {
            // 构造响应数据包
            ByteBuf response = Unpooled.buffer();
            response.writeShort(index);
            response.writeShort(type);
            response.writeByte(RESULT_SUCCESS);
            // 发送格式化后的网络消息
            channel.writeAndFlush(new NetworkMessage(
                    formatMessage(delimiter, MSG_GENERAL_RESPONSE, id, false, response), remoteAddress));
        }
    }

    /**
     * 发送第二种通用响应消息
     *
     * @param channel 网络通道，用于发送响应数据
     * @param remoteAddress 远程地址，指定响应发送的目标地址
     * @param id 设备ID，用于标识响应的目标设备
     * @param type 响应类型，表示响应的消息类型
     */
    private void sendGeneralResponse2(
            Channel channel, SocketAddress remoteAddress, ByteBuf id, int type) {
        if (channel != null) {
            // 构造响应数据包
            ByteBuf response = Unpooled.buffer();
            response.writeShort(type);
            response.writeByte(RESULT_SUCCESS);
            // 发送格式化后的网络消息
            channel.writeAndFlush(new NetworkMessage(
                    formatMessage(delimiter, MSG_GENERAL_RESPONSE_2, id, true, response), remoteAddress));
        }
    }


    /**
     * 根据设备型号和报警值解析并设置位置对象的报警状态。
     *
     * @param position 位置对象，用于存储解析出的报警信息
     * @param model 设备型号，决定使用哪种报警解析逻辑
     * @param value 报警值，通过位运算判断具体的报警类型
     */
    private void decodeAlarm(Position position, String model, long value) {
        // 针对特定型号 G-360P 和 G-508P 的报警解析逻辑
        if (model != null && Set.of("G-360P", "G-508P").contains(model)) {
            if (BitUtil.check(value, 0) || BitUtil.check(value, 4)) {   // 通过 BitUtil.check 检查 value 的第 0 位或第 4 位是否为 1，若任一条件成立，则向 position 添加 ALARM_REMOVING 报警类型。用于判断设备是否被拆除。
                position.addAlarm(Position.ALARM_REMOVING);
            }
            if (BitUtil.check(value, 1)) {
                position.addAlarm(Position.ALARM_TAMPERING);
            }
        // 针对 AL300 和 GL100 型号的报警解析逻辑
        } else if (model != null && Set.of("AL300", "GL100").contains(model)) {
            if (BitUtil.check(value, 16)) {
                position.addAlarm(Position.ALARM_MOVEMENT);
            }
        // 默认报警解析逻辑，适用于其他未特别指定的设备型号
        } else {
            if (BitUtil.check(value, 0)) {
                position.addAlarm(Position.ALARM_SOS);
            }
            if (BitUtil.check(value, 1)) {
                position.addAlarm(Position.ALARM_OVERSPEED);
            }
            if (BitUtil.check(value, 5)) {
                position.addAlarm(Position.ALARM_GPS_ANTENNA_CUT);
            }
            // 检查多种故障相关位
            if (BitUtil.check(value, 4) || BitUtil.check(value, 9)
                    || BitUtil.check(value, 10) || BitUtil.check(value, 11)) {
                position.addAlarm(Position.ALARM_FAULT);
            }
            // 检查低电量相关位
            if (BitUtil.check(value, 7) || BitUtil.check(value, 18)) {
                position.addAlarm(Position.ALARM_LOW_BATTERY);
            }
            if (BitUtil.check(value, 8)) {
                position.addAlarm(Position.ALARM_POWER_OFF);
            }
            if (BitUtil.check(value, 15)) {
                position.addAlarm(Position.ALARM_VIBRATION);
            }
            // 检查防拆相关位
            if (BitUtil.check(value, 16) || BitUtil.check(value, 17)) {
                position.addAlarm(Position.ALARM_TAMPERING);
            }
            if (BitUtil.check(value, 20)) {
                position.addAlarm(Position.ALARM_GEOFENCE);
            }
            if (BitUtil.check(value, 28)) {
                position.addAlarm(Position.ALARM_MOVEMENT);
            }
            // 检查事故相关位，并排除 VL300 型号
            if (BitUtil.check(value, 29) || BitUtil.check(value, 30)) {
                if (model == null || !model.equals("VL300")) {
                    position.addAlarm(Position.ALARM_ACCIDENT);
                }
            }
        }
    }


    /**
     * 从字节缓冲区中读取一个有符号的16位整数
     *
     * @param buf 字节缓冲区
     * @return 有符号的16位整数值
     */
    private int readSignedWord(ByteBuf buf) {
        int value = buf.readUnsignedShort();
        // 检查符号位(第15位)，如果为1则表示负数，需要转换为负值
        return BitUtil.check(value, 15) ? -BitUtil.to(value, 15) : BitUtil.to(value, 15);
    }

    /**
     * 从字节缓冲区中读取日期时间信息并构建Date对象
     *
     * @param buf 字节缓冲区
     * @param timeZone 时区信息
     * @return 构建的Date对象
     */
    private Date readDate(ByteBuf buf, TimeZone timeZone) {
        // 依次读取年、月、日、时、分、秒信息构建日期对象
        DateBuilder dateBuilder = new DateBuilder(timeZone)
                .setYear(BcdUtil.readInteger(buf, 2))
                .setMonth(BcdUtil.readInteger(buf, 2))
                .setDay(BcdUtil.readInteger(buf, 2))
                .setHour(BcdUtil.readInteger(buf, 2))
                .setMinute(BcdUtil.readInteger(buf, 2))
                .setSecond(BcdUtil.readInteger(buf, 2));
        return dateBuilder.getDate();
    }


    /**
     * 解码设备ID
     *
     * @param id 包含设备ID信息的ByteBuf对象
     * @return 解码后的设备ID字符串
     */
    private String decodeId(ByteBuf id) {
        // 将字节缓冲区转换为十六进制字符串
        String serial = ByteBufUtil.hexDump(id);

        // 如果序列号只包含数字，则直接返回
        if (serial.matches("[0-9]+")) {
            return serial;
        } else {
            // 否则按照IMEI格式解析：前2字节+后4字节组合成long型IMEI号，再追加校验位
            long imei = id.getUnsignedShort(0);
            imei = (imei << 32) + id.getUnsignedInt(2);
            return String.valueOf(imei) + Checksum.luhn(imei);
        }
    }


    /**
     * 解析OBD实时数据并设置到位置对象中
     *
     * @param position 位置对象，用于存储解析后的OBD数据
     * @param data 包含OBD实时数据的字符串，各数据项以逗号分隔
     */
    private void decodeObdRt(Position position, String data) {
        String[] values = data.split(",");
        int index = 1; // skip header

        // 解析并设置车辆电源电压
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_POWER, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置发动机转速
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_RPM, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置OBD速度
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_OBD_SPEED, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置节气门开度
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_THROTTLE, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置发动机负荷
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_ENGINE_LOAD, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置冷却液温度
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_COOLANT_TEMP, Integer.parseInt(values[index - 1]));
        }

        // 解析并设置瞬时燃油消耗
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_FUEL_CONSUMPTION, Double.parseDouble(values[index - 1])); // instant
        }

        // 解析并设置平均燃油消耗
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_FUEL_CONSUMPTION, Double.parseDouble(values[index - 1])); // average
        }

        // 解析并设置行程里程
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_ODOMETER_TRIP, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置OBD里程表读数
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_OBD_ODOMETER, Double.parseDouble(values[index - 1]));
        }

        // 解析并设置行程燃油使用量
        if (!values[index++].isEmpty()) {
            position.set("tripFuelUsed", Double.parseDouble(values[index - 1]));
        }

        // 解析并设置燃油使用总量
        if (!values[index++].isEmpty()) {
            position.set(Position.KEY_FUEL_USED, Double.parseDouble(values[index - 1]));
        }
    }


    @Override
    /**
     * 解码从通道接收到的消息，根据消息类型进行不同的处理。
     *
     * @param channel 当前通信的通道，用于发送响应或数据回传
     * @param remoteAddress 远程地址信息，标识消息来源
     * @param msg 原始接收的消息内容，预期为 ByteBuf 类型
     * @return 解码后的对象，可能是 Position、null 或其他业务相关对象
     * @throws Exception 如果在解码过程中发生错误
     */
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        // 判断是否是 ASCII 格式的文本消息（以 '(' 开头）
        if (buf.getByte(buf.readerIndex()) == '(') {
            String sentence = buf.toString(StandardCharsets.US_ASCII);

            // 如果包含 "BASE,2" 字符串，则认为是时间同步请求
            if (sentence.contains("BASE,2")) {
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String response = sentence.replace("TIME", dateFormat.format(new Date()));

                // 发送时间同步响应
                if (channel != null) {
                    channel.writeAndFlush(new NetworkMessage(
                            Unpooled.copiedBuffer(response, StandardCharsets.US_ASCII), remoteAddress));
                }
                return null;
            } else {
                // 否则调用 decodeResult 处理文本消息
                return decodeResult(channel, remoteAddress, sentence);
            }
        }

        // 读取消息头部字段
        delimiter = buf.readUnsignedByte();
        int type = buf.readUnsignedShort();
        int attribute = buf.readUnsignedShort();
        ByteBuf id = buf.readSlice(isAlternative() ? 7 : 6);
        int index;

        // 根据消息类型决定索引字段长度
        if (type == MSG_LOCATION_REPORT_2 || type == MSG_LOCATION_REPORT_BLIND) {
            index = buf.readUnsignedByte();
        } else {
            index = buf.readUnsignedShort();
        }

        // 获取设备会话信息
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, decodeId(id));
        if (deviceSession == null) {
            return null;
        }

        // 设置默认时区（如果尚未设置）
        if (!deviceSession.contains(DeviceSession.KEY_TIMEZONE)) {
            deviceSession.set(DeviceSession.KEY_TIMEZONE, getTimeZone(deviceSession.getDeviceId(), "GMT+8"));
        }

        // 根据消息类型进行分支处理
        if (type == MSG_TERMINAL_REGISTER) {

            // 终端注册消息处理
            if (channel != null) {
                ByteBuf response = Unpooled.buffer();
                response.writeShort(index);
                response.writeByte(RESULT_SUCCESS);
                response.writeBytes(decodeId(id).getBytes(StandardCharsets.US_ASCII));
                channel.writeAndFlush(new NetworkMessage(
                        formatMessage(delimiter, MSG_TERMINAL_REGISTER_RESPONSE, id, false, response), remoteAddress));
            }

        } else if (type == MSG_REPORT_TEXT_MESSAGE) {

            // 文本消息上报处理
            sendGeneralResponse(channel, remoteAddress, id, type, index);

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);

            buf.readUnsignedByte(); // encoding
            Charset charset = Charset.isSupported("GBK") ? Charset.forName("GBK") : StandardCharsets.US_ASCII;

            position.set(Position.KEY_RESULT, buf.readCharSequence(buf.readableBytes() - 2, charset).toString());

            return position;

        } else if (type == MSG_TERMINAL_AUTH || type == MSG_HEARTBEAT || type == MSG_HEARTBEAT_2 || type == MSG_PHOTO) {

            // 心跳、认证、照片等通用响应处理
            sendGeneralResponse(channel, remoteAddress, id, type, index);

        } else if (type == MSG_LOCATION_REPORT) {

            // 实时位置上报处理
            sendGeneralResponse(channel, remoteAddress, id, type, index);

            return decodeLocation(deviceSession, buf);

        } else if (type == MSG_LOCATION_REPORT_2 || type == MSG_LOCATION_REPORT_BLIND) {

            // 特殊位置上报处理（带属性标志）
            if (BitUtil.check(attribute, 15)) {
                sendGeneralResponse2(channel, remoteAddress, id, type);
            }

            return decodeLocation2(deviceSession, buf, type);

        } else if (type == MSG_LOCATION_BATCH || type == MSG_LOCATION_BATCH_2) {

            // 批量位置上报处理
            sendGeneralResponse(channel, remoteAddress, id, type, index);

            return decodeLocationBatch(deviceSession, buf, type);

        } else if (type == MSG_TIME_SYNC_REQUEST) {

            // 时间同步请求处理
            if (channel != null) {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                ByteBuf response = Unpooled.buffer();
                response.writeShort(calendar.get(Calendar.YEAR));
                response.writeByte(calendar.get(Calendar.MONTH) + 1);
                response.writeByte(calendar.get(Calendar.DAY_OF_MONTH));
                response.writeByte(calendar.get(Calendar.HOUR_OF_DAY));
                response.writeByte(calendar.get(Calendar.MINUTE));
                response.writeByte(calendar.get(Calendar.SECOND));
                channel.writeAndFlush(new NetworkMessage(
                        formatMessage(delimiter, MSG_TERMINAL_REGISTER_RESPONSE, id, false, response), remoteAddress));
            }

        } else if (type == MSG_ACCELERATION) {

            // 加速度传感器数据处理
            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);

            StringBuilder data = new StringBuilder("[");
            while (buf.readableBytes() > 2) {
                buf.skipBytes(6); // time
                if (data.length() > 1) {
                    data.append(",");
                }
                data.append("[");
                data.append(readSignedWord(buf));
                data.append(",");
                data.append(readSignedWord(buf));
                data.append(",");
                data.append(readSignedWord(buf));
                data.append("]");
            }
            data.append("]");

            position.set(Position.KEY_G_SENSOR, data.toString());

            return position;

        } else if (type == MSG_TRANSPARENT) {

            // 透明传输消息处理
            sendGeneralResponse(channel, remoteAddress, id, type, index);

            return decodeTransparent(deviceSession, buf);

        } else if (type == MSG_COMMAND_RESPONSE) {

            // 命令响应处理
            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);

            String result = buf.readCharSequence(buf.readInt(), StandardCharsets.US_ASCII).toString();
            position.set(Position.KEY_RESULT, result);

            return position;

        }

        return null;
    }

    /**
     * 解码文本格式的结果消息。
     *
     * @param channel 当前通信的通道
     * @param remoteAddress 远程地址信息
     * @param sentence 完整的文本消息内容
     * @return 包含结果信息的位置对象，若无法获取设备会话则返回 null
     */
    private Position decodeResult(Channel channel, SocketAddress remoteAddress, String sentence) {
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
        if (deviceSession != null) {
            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());
            getLastLocation(position, null);
            position.set(Position.KEY_RESULT, sentence);
            return position;
        }
        return null;
    }


    /**
     * 解析并处理位置数据中的扩展信息字段。
     * <p>
     * 该方法从给定的字节缓冲区中读取一系列类型-长度-值（TLV）格式的扩展字段，
     * 并根据字段类型解析出相应的数据，设置到 Position 对象中。
     * </p>
     *
     * @param position 用于存储解析后数据的位置对象
     * @param buf      包含扩展字段数据的字节缓冲区
     * @param endIndex 扩展字段数据在缓冲区中的结束位置索引
     */
    private void decodeExtension(Position position, ByteBuf buf, int endIndex) {
        // 循环读取扩展字段，直到达到指定的结束位置
        while (buf.readerIndex() < endIndex) {
            int type = buf.readUnsignedByte();   // 读取字段类型
            int length = buf.readUnsignedByte(); // 读取字段长度

            // 根据字段类型解析并设置对应的数据
            switch (type) {
                case 0x01 -> position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 100L);
                case 0x02 -> position.set(Position.KEY_FUEL, buf.readUnsignedShort() * 0.1);
                case 0x03 -> position.set(Position.KEY_OBD_SPEED, buf.readUnsignedShort() * 0.1);
                case 0x56 -> {
                    buf.readUnsignedByte(); // 跳过电源等级字段
                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                }
                case 0x61 -> position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.01);
                case 0x69 -> position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.01);
                case 0x80 -> position.set(Position.KEY_OBD_SPEED, buf.readUnsignedByte());
                case 0x81 -> position.set(Position.KEY_RPM, buf.readUnsignedShort());
                case 0x82 -> position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.1);
                case 0x83 -> position.set(Position.KEY_ENGINE_LOAD, buf.readUnsignedByte());
                case 0x84 -> position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedByte() - 40);
                case 0x85 -> position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort());
                case 0x86 -> position.set("intakeTemp", buf.readUnsignedByte() - 40);
                case 0x87 -> position.set("intakeFlow", buf.readUnsignedShort());
                case 0x88 -> position.set("intakePressure", buf.readUnsignedByte());
                case 0x89 -> position.set(Position.KEY_THROTTLE, buf.readUnsignedByte());
                case 0x8B -> {
                    position.set(Position.KEY_VIN, buf.readCharSequence(17, StandardCharsets.US_ASCII).toString());
                }
                case 0x8C -> position.set(Position.KEY_OBD_ODOMETER, buf.readUnsignedInt() * 100L);
                case 0x8D -> position.set(Position.KEY_ODOMETER_TRIP, buf.readUnsignedShort() * 1000L);
                case 0x8E -> position.set(Position.KEY_FUEL, buf.readUnsignedByte());
                case 0xA0 -> {
                    String codes = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                    position.set(Position.KEY_DTCS, codes.replace(',', ' '));
                }
                case 0xCC -> {
                    position.set(Position.KEY_ICCID, buf.readCharSequence(20, StandardCharsets.US_ASCII).toString());
                }
                default -> buf.skipBytes(length); // 未知类型字段，跳过该字段内容
            }
        }
    }

    /**
     * 解码位置坐标信息
     *
     * @param position 位置对象，用于存储解码后的坐标和状态信息
     * @param deviceSession 设备会话对象，包含设备相关信息
     * @param buf 数据缓冲区，包含待解码的原始数据
     */
    private void decodeCoordinates(Position position, DeviceSession deviceSession, ByteBuf buf) {

        int status = buf.readInt();

        String model = getDeviceModel(deviceSession);

        // 解码通用状态位信息
        position.set(Position.KEY_IGNITION, BitUtil.check(status, 0));
        if ("G1C Pro".equals(model)) {
            position.set(Position.KEY_MOTION, BitUtil.check(status, 4));
        }
        position.set(Position.KEY_BLOCKED, BitUtil.check(status, 10));
        if ("MV810G".equals(model) || "MV710G".equals(model)) {
            position.set(Position.KEY_DOOR, BitUtil.check(status, 16));
        }
        position.set(Position.KEY_CHARGE, BitUtil.check(status, 26));

        position.setValid(BitUtil.check(status, 1));

        // 解码经纬度坐标
        double lat = buf.readUnsignedInt() * 0.000001;
        double lon = buf.readUnsignedInt() * 0.000001;

        // 根据状态位设置坐标的正负号
        if (BitUtil.check(status, 2)) {
            position.setLatitudeWgs84(-lat);
        } else {
            position.setLatitudeWgs84(lat);
        }

        if (BitUtil.check(status, 3)) {
            position.setLongitudeWgs84(-lon);
        } else {
            position.setLongitudeWgs84(lon);
        }
    }

    /**
     * 解码自定义双精度浮点数
     *
     * @param buf 数据缓冲区，包含待解码的原始数据
     * @return 解码后的双精度浮点数值
     */
    private double decodeCustomDouble(ByteBuf buf) {
        int b1 = buf.readByte();
        int b2 = buf.readUnsignedByte();
        int sign = b1 != 0 ? b1 / Math.abs(b1) : 1;
        return sign * (Math.abs(b1) + b2 / 255.0);
    }


     /**
     * 解码位置信息数据包，解析设备上报的位置及相关状态信息
     *
     * @param deviceSession 设备会话对象，包含设备ID和时区等信息
     * @param buf 包含位置数据的字节缓冲区
     * @return 解析后的位置对象Position，包含经纬度、速度、报警等信息
     */
    private Position decodeLocation(DeviceSession deviceSession, ByteBuf buf) {

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        String model = getDeviceModel(deviceSession);

        // 解析报警信息
        decodeAlarm(position, model, buf.readUnsignedInt());

        // 解析坐标和基本状态信息
        decodeCoordinates(position, deviceSession, buf);

        // 设置海拔、速度、航向和时间
        position.setAltitude(buf.readShort());
        position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort() * 0.1));
        position.setCourse(buf.readUnsignedShort());
        position.setTime(readDate(buf, deviceSession.get(DeviceSession.KEY_TIMEZONE)));

        // 如果剩余数据长度为20字节，使用简化解析方式
        if (buf.readableBytes() == 20) {

            buf.skipBytes(4); // 跳过剩余电池和里程数据
            position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 1000);
            position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.1);
            buf.readUnsignedInt(); // 跳过区域ID
            position.set(Position.KEY_RSSI, buf.readUnsignedByte());
            buf.skipBytes(3); // 跳过保留字段

            return position;

        }

        // 创建网络信息对象，用于存储基站和WiFi信息
        Network network = new Network();

        // 循环解析位置附加信息数据
        /* buf.readableBytes():可读字节数
         *   当前还能读取的字节数量，计算公式为：
         *   可读字节数 = 写索引(writerIndex) − 读索引(readerIndex)
         */
        while (buf.readableBytes() > 2) {

            int subtype = buf.readUnsignedByte();  // 附加信息ID, 1 byte
            int length = buf.readUnsignedByte();   // 附加信息长度, 1 byte
            int endIndex = buf.readerIndex() + length;  // 字段结束位置,= 当前读索引+读取长度
            String stringValue;
            int event;

            // 根据字段类型解析不同的数据

            switch (subtype) {
                case 0x01:
                    // 里程数，32位无符号整型
                    position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 100);
                    break;
                case 0x02:
                    // 燃油信息，16位无符号整型
                    int fuel = buf.readUnsignedShort();
                    if (BitUtil.check(fuel, 15)) {
                        position.set(Position.KEY_FUEL, BitUtil.to(fuel, 15));
                    } else {
                        position.set(Position.KEY_FUEL, fuel / 10.0);
                    }
                    break;
                case 0x06:
                    // 车厢温度，16位无符号整型，最高位为1是负数
                    position.set(Position.KEY_DEVICE_TEMP, buf.readShort());
                    break;
                case 0x14:  // 0x14~0x24 保留，
                    // 视频报警
                    position.set("videoAlarm", buf.readUnsignedInt());
                    break;
                case 0x25:
                    // 输入信号状态
                    position.set(Position.KEY_INPUT, buf.readUnsignedInt());
                    break;
                case 0x2B:
                case 0xA7:
                    // ADC传感器数据
                    position.set(Position.PREFIX_ADC + 1, buf.readUnsignedShort() / 100.0);
                    position.set(Position.PREFIX_ADC + 2, buf.readUnsignedShort() / 100.0);
                    break;
                case 0x30:
                    // 信号强度
                    position.set(Position.KEY_RSSI, buf.readUnsignedByte());
                    break;
                case 0x31:
                    // 卫星数量
                    position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                    break;
                case 0x33:
                    // 设备模式或锁状态信息
                    if (length == 1) {
                        position.set("mode", buf.readUnsignedByte());
                    } else {
                        stringValue = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                        if (stringValue.startsWith("*M00")) {
                            String lockStatus = stringValue.substring(8, 8 + 7);
                            position.set(Position.KEY_BATTERY, Integer.parseInt(lockStatus.substring(2, 5)) * 0.01);
                        }
                    }
                    break;
                case 0x51:
                    // 温度传感器数据
                    if (length == 2 || length == 16) {
                        for (int i = 1; i <= length / 2; i++) {
                            int value = buf.readUnsignedShort();
                            if (value != 0xffff) {
                                if (BitUtil.check(value, 15)) {
                                    position.set(Position.PREFIX_TEMP + i, -BitUtil.to(value, 15) / 10.0);
                                } else {
                                    position.set(Position.PREFIX_TEMP + i, value / 10.0);
                                }
                            }
                        }
                    }
                    break;
                case 0x56:
                    // 电池电量百分比(特殊格式)
                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte() * 10);
                    buf.readUnsignedByte(); // 跳过保留字段
                    break;
                case 0x57:
                    // 驾驶行为报警
                    int alarm = buf.readUnsignedShort();
                    position.addAlarm(BitUtil.check(alarm, 8) ? Position.ALARM_ACCELERATION : null);
                    position.addAlarm(BitUtil.check(alarm, 9) ? Position.ALARM_BRAKING : null);
                    position.addAlarm(BitUtil.check(alarm, 10) ? Position.ALARM_CORNERING : null);
                    buf.readUnsignedShort(); // 跳过外部开关状态
                    long alarm2 = buf.readUnsignedInt();
                    if ("MV810G".equals(model) || "MV710G".equals(model)) {
                        position.addAlarm(BitUtil.check(alarm2, 16) ? Position.ALARM_DOOR : null);
                    }
                    break;
                case 0x60:
                    // 事件信息
                    event = buf.readUnsignedShort();
                    position.set(Position.KEY_EVENT, event);
                    if (event >= 0x0061 && event <= 0x0066) {
                        buf.skipBytes(6); // 跳过锁ID
                        stringValue = buf.readCharSequence(8, StandardCharsets.US_ASCII).toString();
                        position.set(Position.KEY_DRIVER_UNIQUE_ID, stringValue);
                    }
                    break;
                case 0x61:
                    // 电压
                    long voltage1 = buf.readUnsignedShort();
                    position.set(Position.KEY_POWER, voltage1 * 0.01);
//                    LOG.info("POWER: {}", voltage1 * 0.01);
                    break;
                case 0x63:
                    // 锁信息
                    for (int i = 1; i <= length / 11; i++) {
                        position.set("lock" + i + "Id", ByteBufUtil.hexDump(buf.readSlice(6)));
                        position.set("lock" + i + "Battery", buf.readUnsignedShort() * 0.001);
                        position.set("lock" + i + "Seal", buf.readUnsignedByte() == 0x31);
                        buf.readUnsignedByte(); // 跳过物理状态
                        buf.readUnsignedByte(); // 跳过RSSI
                    }
                    break;
                case 0x64:
                    // ADAS报警信息
                    buf.readUnsignedInt(); // 跳过报警序列号
                    buf.readUnsignedByte(); // 跳过报警状态
                    position.set("adasAlarm", buf.readUnsignedByte());
                    break;
                case 0x65:
                    // DMS报警信息
                    buf.readUnsignedInt(); // 跳过报警序列号
                    buf.readUnsignedByte(); // 跳过报警状态
                    position.set("dmsAlarm", buf.readUnsignedByte());
                    break;
                case 0x67:
                    // 密码信息
                    stringValue = buf.readCharSequence(8, StandardCharsets.US_ASCII).toString();
                    position.set("password", stringValue);
                    break;
                case 0x70:
                    // 报警事件
                    buf.readUnsignedInt(); // 跳过报警序列号
                    buf.readUnsignedByte(); // 跳过报警状态
                    switch (buf.readUnsignedByte()) {
                        case 0x01 -> position.addAlarm(Position.ALARM_ACCELERATION);
                        case 0x02 -> position.addAlarm(Position.ALARM_BRAKING);
                        case 0x03 -> position.addAlarm(Position.ALARM_CORNERING);
                        case 0x16 -> position.addAlarm(Position.ALARM_ACCIDENT);
                    }
                    break;
                case 0x68:
                    // 电池电量百分比(高精度)
                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedShort() * 0.01);
                    break;
                case 0x69:
                    // 电池电压
                    position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.01);
                    break;
                case 0x77:
                    // 轮胎传感器数据
                    while (buf.readerIndex() < endIndex) {
                        int tireIndex = buf.readUnsignedByte();
                        position.set("tire" + tireIndex + "SensorId", ByteBufUtil.hexDump(buf.readSlice(3)));
                        position.set("tire" + tireIndex + "Pressure", BitUtil.to(buf.readUnsignedShort(), 10) / 40.0);
                        position.set("tire" + tireIndex + "Temp", buf.readUnsignedByte() - 50);
                        position.set("tire" + tireIndex + "Status", buf.readUnsignedByte());
                    }
                    break;
                case 0x80:
                    // 扩展字段数据
                    buf.readUnsignedByte(); // 跳过内容标识
                    endIndex = buf.writerIndex() - 2;
                    decodeExtension(position, buf, endIndex);
                    break;
                case 0x82:
                    // 电源电压
                    position.set(Position.KEY_POWER, buf.readUnsignedShort() / 10.0);
                    break;
                case 0x91:
                    // OBD数据
                    position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.1);
                    position.set(Position.KEY_RPM, buf.readUnsignedShort());
                    position.set(Position.KEY_OBD_SPEED, buf.readUnsignedByte());
                    position.set(Position.KEY_THROTTLE, buf.readUnsignedByte() * 100 / 255);
                    position.set(Position.KEY_ENGINE_LOAD, buf.readUnsignedByte() * 100 / 255);
                    position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedByte() - 40);
                    buf.readUnsignedShort();
                    position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort() * 0.01);
                    buf.readUnsignedShort();
                    buf.readUnsignedInt();
                    buf.readUnsignedShort();
                    position.set(Position.KEY_FUEL_USED, buf.readUnsignedShort() * 0.01);
                    break;
                case 0x94:
                    // VIN码
                    if (length > 0) {
                        stringValue = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                        position.set(Position.KEY_VIN, stringValue);
                    }
                    break;
                case 0xAC:
                    // 里程数
                    position.set(Position.KEY_ODOMETER, buf.readUnsignedInt());
                    break;
                case 0xBC:
                    // 司机信息
                    stringValue = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                    position.set("driver", stringValue.trim());
                    break;
                case 0xBD:
                    // 司机唯一标识
                    stringValue = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                    position.set(Position.KEY_DRIVER_UNIQUE_ID, stringValue);
                    break;
                case 0xD0:
                    // 用户状态报警
                    long userStatus = buf.readUnsignedInt();
                    if (BitUtil.check(userStatus, 3)) {
                        position.addAlarm(Position.ALARM_VIBRATION);
                    }
                    break;
                case 0xD3:
                    // 电源电压
                    position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.1);
                    break;
                case 0xD4:
                case 0xE1:
                    // 电池电量或司机ID
                    if (length == 1) {
                        position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                    } else {
                        position.set(Position.KEY_DRIVER_UNIQUE_ID, String.valueOf(buf.readUnsignedInt()));
                    }
                    break;
                case 0xD5:
                    // 电池电压或锁信息
                    if (length == 2) {
                        position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.01);
                    } else {
                        int count = buf.readUnsignedByte();
                        for (int i = 1; i <= count; i++) {
                            position.set("lock" + i + "Id", ByteBufUtil.hexDump(buf.readSlice(5)));
                            position.set("lock" + i + "Card", ByteBufUtil.hexDump(buf.readSlice(5)));
                            position.set("lock" + i + "Battery", buf.readUnsignedByte());
                            int status = buf.readUnsignedShort();
                            position.set("lock" + i + "Locked", !BitUtil.check(status, 5));
                        }
                    }
                    break;
                case 0xDA:
                    // 设备状态
                    buf.readUnsignedShort(); // 跳过字符串切割计数
                    int deviceStatus = buf.readUnsignedByte();
                    position.set("string", BitUtil.check(deviceStatus, 0));
                    position.set(Position.KEY_MOTION, BitUtil.check(deviceStatus, 2));
                    position.set("cover", BitUtil.check(deviceStatus, 3));
                    break;
                case 0xE2:
                    // 燃油信息
                    if (!"DT800".equals(model)) {
                        position.set(Position.KEY_FUEL, buf.readUnsignedInt() * 0.1);
                    }
                    break;
                case 0xE3:
                    // 电池状态
                    buf.readUnsignedByte(); // 跳过保留字段
                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                    position.set(Position.KEY_BATTERY, buf.readUnsignedShort() / 100.0);
                    break;
                case 0xE4:
                    // 充电状态和电池电量
                    if (buf.readUnsignedByte() == 0) {
                        position.set(Position.KEY_CHARGE, true);
                    }
                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                    break;
                case 0xE6:
                    // OBD实时数据或温湿度传感器数据
                    String header = buf.getCharSequence(buf.readerIndex(), 7, StandardCharsets.UTF_8).toString();
                    if (header.equals("$OBD-RT")) {
                        String data = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
                        decodeObdRt(position, data);
                    } else {
                        while (buf.readerIndex() < endIndex) {
                            int sensorIndex = buf.readUnsignedByte();
                            buf.skipBytes(6); // 跳过MAC地址
                            position.set(Position.PREFIX_TEMP + sensorIndex, decodeCustomDouble(buf));
                            position.set("humidity" + sensorIndex, decodeCustomDouble(buf));
                        }
                    }
                    break;
                case 0xEA:
                    // 外部传感器信息
                    if (length > 2) {
                        buf.readUnsignedByte(); // 跳过扩展信息类型
                        while (buf.readerIndex() < endIndex) {
                            int extendedType = buf.readUnsignedByte();
                            int extendedLength = buf.readUnsignedByte();
                            int extendedEndIndex = buf.readerIndex() + extendedLength;
                            switch (extendedType) {
                                case 0x11:
                                    position.set("externalAlarms", buf.readUnsignedShort());
                                    position.set("alarmThresholdType", buf.readUnsignedByte());
                                    buf.readUnsignedInt(); // 跳过上限阈值
                                    buf.readUnsignedInt(); // 跳过当前值
                                    buf.readUnsignedInt(); // 跳过下限阈值
                                    break;
                                case 0x13:
                                    position.set("externalIlluminance", buf.readUnsignedShort());
                                    break;
                                case 0x14:
                                    position.set("externalAirPressure", buf.readUnsignedShort());
                                    break;
                                case 0x15:
                                    position.set("externalHumidity", buf.readUnsignedShort() / 10.0);
                                    break;
                                case 0x16:
                                    position.set("externalTemp", buf.readUnsignedShort() / 10.0 - 50);
                                    break;
                                default:
                                    break;
                            }
                            buf.readerIndex(extendedEndIndex);
                        }
                    }
                    break;
                case 0xEB:
                    // 基站或WiFi信息
                    if (buf.getUnsignedShort(buf.readerIndex()) > 200) {
                        int mcc = buf.readUnsignedShort();
                        int mnc = buf.readUnsignedByte();
                        while (buf.readerIndex() < endIndex) {
                            network.addCellTower(CellTower.from(
                                    mcc, mnc, buf.readUnsignedShort(), buf.readUnsignedShort(),
                                    buf.readUnsignedByte()));
                        }
                    } else {
                        while (buf.readerIndex() < endIndex) {
                            int extendedLength = buf.readUnsignedShort();
                            int extendedEndIndex = buf.readerIndex() + extendedLength;
                            int extendedType = buf.readUnsignedShort();
                            switch (extendedType) {
                                case 0x0001:
                                    position.set("fuel1", buf.readUnsignedShort() * 0.1);
                                    buf.readUnsignedByte(); // 跳过未使用字段
                                    break;
                                case 0x0023:
                                    position.set("fuel2", Double.parseDouble(
                                            buf.readCharSequence(6, StandardCharsets.US_ASCII).toString()));
                                    break;
                                case 0x00B2:
                                    position.set(Position.KEY_ICCID, ByteBufUtil.hexDump(
                                            buf.readSlice(10)).replaceAll("f", ""));
                                    break;
                                case 0x00B9:
                                    buf.readUnsignedByte(); // 跳过计数
                                    String[] wifi = buf.readCharSequence(
                                            extendedLength - 3, StandardCharsets.US_ASCII).toString().split(",");
                                    for (int i = 0; i < wifi.length / 2; i++) {
                                        network.addWifiAccessPoint(
                                                WifiAccessPoint.from(wifi[i * 2], Integer.parseInt(wifi[i * 2 + 1])));
                                    }
                                    break;
                                case 0x00C6:
                                    int batteryAlarm = buf.readUnsignedByte();
                                    if (batteryAlarm == 0x03 || batteryAlarm == 0x04) {
                                        position.set(Position.KEY_ALARM, Position.ALARM_LOW_BATTERY);
                                    }
                                    position.set("batteryAlarm", batteryAlarm);
                                    break;
                                case 0x00CE:
                                    position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.01);
                                    break;
                                case 0x00D8:
                                    network.addCellTower(CellTower.from(
                                            buf.readUnsignedShort(), buf.readUnsignedByte(),
                                            buf.readUnsignedShort(), buf.readUnsignedInt()));
                                    break;
                                case 0x00A8:
                                case 0x00E1:
                                    position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                                    break;
                                default:
                                    break;
                            }
                            buf.readerIndex(extendedEndIndex);
                        }
                    }
                    break;
                case 0xED:
                    // 卡片信息
                    stringValue = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
                    position.set(Position.KEY_CARD, stringValue.trim());
                    break;
                case 0xEE:
                    // 网络信号信息
                    position.set(Position.KEY_RSSI, buf.readUnsignedByte());
                    position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.001);
                    position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.001);
                    position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                    break;
                case 0xF1:
                    // ICCID信息
                    String iccid = buf.readSlice(length).toString(StandardCharsets.US_ASCII);
                    position.set(Position.KEY_ICCID, iccid);
//                    LOG.info("ICCID: {}", iccid);
//                    position.set(Position.KEY_POWER, voltage * 0.001);
                    break;
                case 0xF3:
                    // OBD扩展数据
                    while (buf.readerIndex() < endIndex) {
                        int extendedType = buf.readUnsignedShort();
                        int extendedLength = buf.readUnsignedByte();
                        switch (extendedType) {
                            case 0x0002 -> position.set(Position.KEY_OBD_SPEED, buf.readUnsignedShort() * 0.1);
                            case 0x0003 -> position.set(Position.KEY_RPM, buf.readUnsignedShort());
                            case 0x0004 -> position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.001);
                            case 0x0005 -> position.set(Position.KEY_OBD_ODOMETER, buf.readUnsignedInt() * 100);
                            case 0x0007 -> position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort() * 0.1);
                            case 0x0008 -> position.set(Position.KEY_ENGINE_LOAD, buf.readUnsignedShort() * 0.1);
                            case 0x0009 -> position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedShort() - 40);
                            case 0x000B -> position.set("intakePressure", buf.readUnsignedShort());
                            case 0x000C -> position.set("intakeTemp", buf.readUnsignedShort() - 40);
                            case 0x000D -> position.set("intakeFlow", buf.readUnsignedShort());
                            case 0x000E -> position.set(Position.KEY_THROTTLE, buf.readUnsignedShort() * 100 / 255);
                            case 0x0050 -> position.set(Position.KEY_VIN, BufferUtil.readString(buf, 17));
                            case 0x0051 -> {
                                if (extendedLength > 0) {
                                    position.set("cvn", ByteBufUtil.hexDump(buf.readSlice(extendedLength)));
                                }
                            }
                            case 0x0052 -> {
                                if (extendedLength > 0) {
                                    position.set("calid", BufferUtil.readString(buf, extendedLength));
                                }
                            }
                            case 0x0100 -> position.set(Position.KEY_ODOMETER_TRIP, buf.readUnsignedShort() * 0.1);
                            case 0x0102 -> position.set("tripFuel", buf.readUnsignedShort() * 0.1);
                            case 0x0112 -> position.set("hardAccelerationCount", buf.readUnsignedShort());
                            case 0x0113 -> position.set("hardDecelerationCount", buf.readUnsignedShort());
                            case 0x0114 -> position.set("hardCorneringCount", buf.readUnsignedShort());
                            default -> buf.skipBytes(extendedLength);
                        }
                    }
                    break;
                case 0xF4:
                    // WiFi接入点信息
                    while (buf.readerIndex() < endIndex) {
                        String mac = ByteBufUtil.hexDump(buf.readSlice(length)).replaceAll("(..)", "$1:");
                        network.addWifiAccessPoint(WifiAccessPoint.from(
                                mac.substring(0, mac.length() - 1), buf.readByte()));
                    }
                    break;
                case 0xF5:
                    // 照度传感器
                    if (length == 2) {
                        position.set("illuminance", buf.readUnsignedShort());
                    }
                    break;
                case 0xF6:
                    // 气压传感器或环境传感器
                    if (length == 2) {
                        position.set("airPressure", buf.readUnsignedShort());
                    } else {
                        event = buf.readUnsignedByte();
                        position.set(Position.KEY_EVENT, event);
                        if (event == 2) {
                            position.set(Position.KEY_MOTION, true);
                        }
                        int fieldMask = buf.readUnsignedByte();
                        if (BitUtil.check(fieldMask, 0)) {
                            position.set("lightSensor", buf.readUnsignedShort());
                        }
                        if (BitUtil.check(fieldMask, 1)) {
                            position.set(Position.PREFIX_TEMP + 1, buf.readShort() * 0.1);
                        }
                        if (BitUtil.check(fieldMask, 2)) {
                            position.set(Position.KEY_HUMIDITY, buf.readShort() * 0.1);
                        }
                    }
                    break;
                case 0xF7:
                    // 湿度传感器或电池状态
                    if (length == 2) {
                        position.set(Position.KEY_HUMIDITY, buf.readUnsignedShort() / 10.0);
                    } else {
                        position.set(Position.KEY_BATTERY, buf.readUnsignedInt() * 0.001);
                        if (length >= 5) {
                            short batteryStatus = buf.readUnsignedByte();
                            if (batteryStatus == 2 || batteryStatus == 3) {
                                position.set(Position.KEY_CHARGE, true);
                            }
                        }
                        if (length >= 6) {
                            position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                        }
                    }
                    break;
                case 0xF8:
                    // 温度传感器
                    position.set(Position.PREFIX_TEMP + 2, buf.readUnsignedShort() / 10.0 - 50);
                    break;
                case 0xFB:
                    // 容器信息
                    position.set("container", buf.readCharSequence(length, StandardCharsets.US_ASCII).toString());
                    break;
                case 0xFC:
                    // 地理围栏
                    position.set(Position.KEY_GEOFENCE, buf.readUnsignedByte());
                    break;
                case 0xFE:
                    // 电池状态信息
                    if (length == 1) {
                        position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                    } else if (length == 2) {
                        position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.1);
                    } else {
                        int mark = buf.readUnsignedByte();
                        if (mark == 0x7C) {
                            while (buf.readerIndex() < endIndex) {
                                int extendedType = buf.readUnsignedByte();
                                int extendedLength = buf.readUnsignedByte();
                                if (extendedType == 0x01) {
                                    long alarms = buf.readUnsignedInt();
                                    if (BitUtil.check(alarms, 0)) {
                                        position.addAlarm(Position.ALARM_ACCELERATION);
                                    }
                                    if (BitUtil.check(alarms, 1)) {
                                        position.addAlarm(Position.ALARM_BRAKING);
                                    }
                                    if (BitUtil.check(alarms, 2)) {
                                        position.addAlarm(Position.ALARM_CORNERING);
                                    }
                                    if (BitUtil.check(alarms, 3)) {
                                        position.addAlarm(Position.ALARM_ACCIDENT);
                                    }
                                    if (BitUtil.check(alarms, 4)) {
                                        position.addAlarm(Position.ALARM_TAMPERING);
                                    }
                                } else {
                                    buf.skipBytes(extendedLength);
                                }
                            }
                        }
                        position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                    }
                    break;
                default:
                    break;
            }
            // 调整读取位置到字段末尾
            buf.readerIndex(endIndex);
        }

        // 如果存在网络信息，设置到位置对象中
        if (network.getCellTowers() != null || network.getWifiAccessPoints() != null) {
            position.setNetwork(network);
        }
        return position;
    }

    /**
     * 解析设备上报的位置信息（类型2），并填充到 Position 对象中。
     *
     * @param deviceSession 当前设备会话对象，用于获取设备ID等信息
     * @param buf           包含位置数据的字节缓冲区
     * @param type          消息类型，用于判断定位是否有效
     * @return              解析后的位置对象 Position
     */
    private Position decodeLocation2(DeviceSession deviceSession, ByteBuf buf, int type) {

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        // 解析二进制格式的位置信息
        Jt600ProtocolDecoder.decodeBinaryLocation(buf, position);
        position.setValid(type != MSG_LOCATION_REPORT_BLIND);

        // 设置信号强度、卫星数量和里程数
        position.set(Position.KEY_RSSI, buf.readUnsignedByte());
        position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
        position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 1000L);

        // 处理电池状态：电量百分比或充电状态
        int battery = buf.readUnsignedByte();
        if (battery <= 100) {
            position.set(Position.KEY_BATTERY_LEVEL, battery);
        } else if (battery == 0xAA || battery == 0xAB) {
            position.set(Position.KEY_CHARGE, true);
        }

        // 提取基站信息 CID 和 LAC，并构建网络信息
        long cid = buf.readUnsignedInt();
        int lac = buf.readUnsignedShort();
        if (cid > 0 && lac > 0) {
            position.setNetwork(new Network(CellTower.fromCidLac(getConfig(), cid, lac)));
        }

        // 读取产品型号、状态码和报警标志
        int product = buf.readUnsignedByte();
        int status = buf.readUnsignedShort();
        int alarm = buf.readUnsignedShort();

        // 根据不同产品型号处理报警逻辑
        if (product == 1 || product == 2) {
            if (BitUtil.check(alarm, 0)) {
                position.addAlarm(Position.ALARM_LOW_POWER);
            }
        } else if (product == 3) {
            position.set(Position.KEY_BLOCKED, BitUtil.check(status, 5));
            if (BitUtil.check(alarm, 0)) {
                position.addAlarm(Position.ALARM_OVERSPEED);
            }
            if (BitUtil.check(alarm, 1)) {
                position.addAlarm(Position.ALARM_LOW_POWER);
            }
            if (BitUtil.check(alarm, 2)) {
                position.addAlarm(Position.ALARM_VIBRATION);
            }
            if (BitUtil.check(alarm, 3)) {
                position.addAlarm(Position.ALARM_LOW_BATTERY);
            }
            if (BitUtil.check(alarm, 5)) {
                position.addAlarm(Position.ALARM_GEOFENCE_ENTER);
            }
            if (BitUtil.check(alarm, 6)) {
                position.addAlarm(Position.ALARM_GEOFENCE_EXIT);
            }
        }

        // 存储原始状态码
        position.set(Position.KEY_STATUS, status);

        // 遍历扩展字段，解析附加信息
        while (buf.readableBytes() > 2) {
            int id = buf.readUnsignedByte();
            int length = buf.readUnsignedByte();
            switch (id) {
                case 0x02:
                    position.setAltitude(buf.readShort());
                    break;
                case 0x10:
                    position.set("wakeSource", buf.readUnsignedByte());
                    break;
                case 0x0A:
                    if (length == 3) {
                        buf.readUnsignedShort(); // mcc
                        buf.readUnsignedByte(); // mnc
                    } else {
                        buf.skipBytes(length);
                    }
                    break;
                case 0x0B:
                    position.set("lockCommand", buf.readUnsignedByte());
                    if (length >= 5 && length <= 6) {
                        position.set("lockCard", buf.readUnsignedInt());
                    } else if (length >= 7) {
                        position.set("lockPassword", buf.readCharSequence(6, StandardCharsets.US_ASCII).toString());
                    }
                    if (length % 2 == 0) {
                        position.set("unlockResult", buf.readUnsignedByte());
                    }
                    break;
                case 0x0C:
                    int x = buf.readUnsignedShort();
                    if (x > 0x8000) {
                        x -= 0x10000;
                    }
                    int y = buf.readUnsignedShort();
                    if (y > 0x8000) {
                        y -= 0x10000;
                    }
                    int z = buf.readUnsignedShort();
                    if (z > 0x8000) {
                        z -= 0x10000;
                    }
                    position.set("tilt", String.format("[%d,%d,%d]", x, y, z));
                    break;
                case 0xFC:
                    position.set(Position.KEY_GEOFENCE, buf.readUnsignedByte());
                    break;
                default:
                    buf.skipBytes(length);
                    break;
            }
        }

        return position;
    }

    /**
     * 批量解码位置信息
     *
     * @param deviceSession 设备会话对象，用于关联设备信息
     * @param buf 包含位置数据的字节缓冲区
     * @param type 消息类型，用于区分不同的批量位置消息格式
     * @return 解码后的位置信息列表
     */
    private List<Position> decodeLocationBatch(DeviceSession deviceSession, ByteBuf buf, int type) {

        List<Position> positions = new LinkedList<>();

        int locationType = 0;
        // 处理MSG_LOCATION_BATCH类型的消息头信息
        if (type == MSG_LOCATION_BATCH) {
            buf.readUnsignedShort(); // count
            locationType = buf.readUnsignedByte();
        }

        // 循环解码每个位置片段
        while (buf.readableBytes() > 2) {
            // 根据消息类型确定长度字段的字节数
            int length = type == MSG_LOCATION_BATCH_2 ? buf.readUnsignedByte() : buf.readUnsignedShort();
            ByteBuf fragment = buf.readSlice(length);
            Position position = decodeLocation(deviceSession, fragment);
            // 如果locationType大于0，标记为归档位置
            if (locationType > 0) {
                position.set(Position.KEY_ARCHIVE, true);
            }
            positions.add(position);
        }

        return positions;
    }


    /**
     * 解码透明传输数据包，根据不同的类型解析出位置信息或其他设备状态数据。
     *
     * @param deviceSession 当前设备会话对象，用于获取设备ID和时区等信息
     * @param buf           包含待解码数据的ByteBuf缓冲区
     * @return 解析后的位置对象(Position)，如果无法解析或数据无效则返回null
     */
    private Position decodeTransparent(DeviceSession deviceSession, ByteBuf buf) {

        int type = buf.readUnsignedByte();

        if (type == 0x40) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);
            String data = buf.readCharSequence(buf.readableBytes(), StandardCharsets.US_ASCII).toString().trim();
            if (data.startsWith("GTSL")) {
                String[] values = data.split("\\|");
                if (values.length > 4) {
                    position.set(Position.KEY_DRIVER_UNIQUE_ID, values[4]);
                }
            }

            return position.getAttributes().isEmpty() ? null : position;

        } else if (type == 0x41) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);

            String data = buf.readCharSequence(buf.readableBytes() - 2, StandardCharsets.US_ASCII).toString().trim();
            decodeObdRt(position, data);

            return position;

        } else if (type == 0xF0) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            Date time = readDate(buf, deviceSession.get(DeviceSession.KEY_TIMEZONE));

            if (buf.readUnsignedByte() > 0) {
                position.set(Position.KEY_ARCHIVE, true);
            }

            buf.readUnsignedByte(); // vehicle type

            int count;
            int subtype = buf.readUnsignedByte();
            switch (subtype) {
                case 0x01:
                    // 解析车辆传感器数据（如里程、油耗、温度等）
                    count = buf.readUnsignedByte();
                    for (int i = 0; i < count; i++) {
                        int id = buf.readUnsignedShort();
                        int length = buf.readUnsignedByte();
                        switch (id) {
                            case 0x0102, 0x0528, 0x0546 -> {
                                position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 100);
                            }
                            case 0x0103 -> position.set(Position.KEY_FUEL, buf.readUnsignedInt() * 0.01);
                            case 0x0111 -> position.set("fuelTemp", buf.readUnsignedByte() - 40);
                            case 0x012E -> position.set("oilLevel", buf.readUnsignedShort() * 0.1);
                            case 0x052A -> position.set(Position.KEY_FUEL, buf.readUnsignedShort() * 0.01);
                            case 0x0105, 0x052C -> position.set(Position.KEY_FUEL_USED, buf.readUnsignedInt() * 0.01);
                            case 0x014A, 0x0537, 0x0538, 0x0539 -> {
                                position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort() * 0.01);
                            }
                            case 0x052B -> position.set(Position.KEY_FUEL, buf.readUnsignedByte());
                            case 0x052D -> position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedByte() - 40);
                            case 0x052E -> position.set("airTemp", buf.readUnsignedByte() - 40);
                            case 0x0530 -> position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.001);
                            case 0x0535 -> position.set(Position.KEY_OBD_SPEED, buf.readUnsignedShort() * 0.1);
                            case 0x0536 -> position.set(Position.KEY_RPM, buf.readUnsignedShort());
                            case 0x053D -> position.set("intakePressure", buf.readUnsignedShort() * 0.1);
                            case 0x0544 -> position.set("liquidLevel", buf.readUnsignedByte());
                            case 0x0547, 0x0548 -> position.set(Position.KEY_THROTTLE, buf.readUnsignedByte());
                            default -> {
                                switch (length) {
                                    case 1 -> position.set(Position.PREFIX_IO + id, buf.readUnsignedByte());
                                    case 2 -> position.set(Position.PREFIX_IO + id, buf.readUnsignedShort());
                                    case 4 -> position.set(Position.PREFIX_IO + id, buf.readUnsignedInt());
                                    default -> buf.skipBytes(length);
                                }
                            }
                        }
                    }
                    getLastLocation(position, time);
                    decodeCoordinates(position, deviceSession, buf);
                    position.setTime(time);
                    break;
                case 0x02:
                    // 解析故障码(DTC)信息
                    List<String> codes = new LinkedList<>();
                    count = buf.readUnsignedShort();
                    for (int i = 0; i < count; i++) {
                        buf.readUnsignedInt(); // system id
                        int codeCount = buf.readUnsignedShort();
                        for (int j = 0; j < codeCount; j++) {
                            buf.readUnsignedInt(); // dtc
                            buf.readUnsignedInt(); // status
                            codes.add(buf.readCharSequence(
                                    buf.readUnsignedShort(), StandardCharsets.US_ASCII).toString().trim());
                        }
                    }
                    position.set(Position.KEY_DTCS, String.join(" ", codes));
                    getLastLocation(position, time);
                    decodeCoordinates(position, deviceSession, buf);
                    position.setTime(time);
                    break;
                case 0x03:
                    // 解析报警事件信息
                    count = buf.readUnsignedByte();
                    for (int i = 0; i < count; i++) {
                        int id = buf.readUnsignedByte();
                        int length = buf.readUnsignedByte();
                        switch (id) {
                            case 0x01:
                                position.addAlarm(Position.ALARM_POWER_RESTORED);
                                break;
                            case 0x02:
                                position.addAlarm(Position.ALARM_POWER_CUT);
                                break;
                            case 0x1A:
                                position.addAlarm(Position.ALARM_ACCELERATION);
                                break;
                            case 0x1B:
                                position.addAlarm(Position.ALARM_BRAKING);
                                break;
                            case 0x1C:
                                position.addAlarm(Position.ALARM_CORNERING);
                                break;
                            case 0x1D:
                            case 0x1E:
                            case 0x1F:
                                position.addAlarm(Position.ALARM_LANE_CHANGE);
                                break;
                            case 0x23:
                                position.addAlarm(Position.ALARM_FATIGUE_DRIVING);
                                break;
                            case 0x26:
                            case 0x27:
                            case 0x28:
                                position.addAlarm(Position.ALARM_ACCIDENT);
                                break;
                            case 0x31:
                            case 0x32:
                                position.addAlarm(Position.ALARM_DOOR);
                                break;
                            default:
                                break;
                        }
                        buf.skipBytes(length);
                    }
                    getLastLocation(position, time);
                    decodeCoordinates(position, deviceSession, buf);
                    position.setTime(time);
                    break;
                case 0x0B:
                    // 解析VIN码信息
                    if (buf.readUnsignedByte() > 0) {
                        position.set(Position.KEY_VIN, buf.readCharSequence(17, StandardCharsets.US_ASCII).toString());
                    }
                    getLastLocation(position, time);
                    break;
                case 0x15:
                    // 解析特定事件编号并映射为报警类型
                    int event = buf.readInt();
                    switch (event) {
                        case 51 -> position.addAlarm(Position.ALARM_ACCELERATION);
                        case 52 -> position.addAlarm(Position.ALARM_BRAKING);
                        case 53 -> position.addAlarm(Position.ALARM_CORNERING);
                        case 54 -> position.addAlarm(Position.ALARM_LANE_CHANGE);
                        case 56 -> position.addAlarm(Position.ALARM_ACCIDENT);
                        default -> position.set(Position.KEY_EVENT, event);
                    }
                    getLastLocation(position, time);
                    break;
                default:
                    return null;
            }

            return position;

        } else if (type == 0xFF) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            position.setValid(true);
            position.setTime(readDate(buf, deviceSession.get(DeviceSession.KEY_TIMEZONE)));
            position.setLatitudeWgs84(buf.readInt() * 0.000001);
            position.setLongitudeWgs84(buf.readInt() * 0.000001);
            position.setAltitude(buf.readShort());
            position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort() * 0.1));
            position.setCourse(buf.readUnsignedShort());

            // TODO more positions and g sensor data
            return position;
        }

        return null;
    }

}
