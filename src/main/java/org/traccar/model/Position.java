/*
 * Copyright 2012 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.model;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.traccar.storage.QueryIgnore;
import org.traccar.storage.StorageName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@StorageName("tc_positions")
public class Position extends Message {

    /**
     * 原始数据键值常量，用于标识未经处理的原始数据
     */
    public static final String KEY_ORIGINAL = "raw";

    /**
     * 索引键值常量，用于标识数据索引位置
     */
    public static final String KEY_INDEX = "index";

    /**
     * 水平精度因子键值常量，用于GPS定位精度描述
     */
    public static final String KEY_HDOP = "hdop";

    /**
     * 垂直精度因子键值常量，用于GPS垂直定位精度描述
     */
    public static final String KEY_VDOP = "vdop";

    /**
     * 位置精度因子键值常量，用于GPS整体定位精度描述
     */
    public static final String KEY_PDOP = "pdop";

    /**
     * 使用中卫星数量键值常量
     */
    public static final String KEY_SATELLITES = "sat"; // in use

    /**
     * 可见卫星数量键值常量
     */
    public static final String KEY_SATELLITES_VISIBLE = "satVisible";

    /**
     * 接收信号强度指示键值常量
     */
    public static final String KEY_RSSI = "rssi";

    /**
     * GPS状态键值常量
     */
    public static final String KEY_GPS = "gps";

    /**
     * 漫游状态键值常量
     */
    public static final String KEY_ROAMING = "roaming";

    /**
     * 事件标识键值常量
     */
    public static final String KEY_EVENT = "event";

    /**
     * 报警状态键值常量
     */
    public static final String KEY_ALARM = "alarm";

    /**
     * 设备状态键值常量
     */
    public static final String KEY_STATUS = "status";

    /**
     * 总里程键值常量，单位为米
     */
    public static final String KEY_ODOMETER = "odometer"; // meters

    /**
     * 服务里程键值常量，单位为米
     */
    public static final String KEY_ODOMETER_SERVICE = "serviceOdometer"; // meters

    /**
     * 行程里程键值常量，单位为米
     */
    public static final String KEY_ODOMETER_TRIP = "tripOdometer"; // meters

    /**
     * 运行时间键值常量，单位为毫秒
     */
    public static final String KEY_HOURS = "hours"; // milliseconds

    /**
     * 步数键值常量
     */
    public static final String KEY_STEPS = "steps";

    /**
     * 心率键值常量
     */
    public static final String KEY_HEART_RATE = "heartRate";

    /**
     * 输入信号键值常量
     */
    public static final String KEY_INPUT = "input";

    /**
     * 输出信号键值常量
     */
    public static final String KEY_OUTPUT = "output";

    /**
     * 图像数据键值常量
     */
    public static final String KEY_IMAGE = "image";

    /**
     * 视频数据键值常量
     */
    public static final String KEY_VIDEO = "video";

    /**
     * 音频数据键值常量
     */
    public static final String KEY_AUDIO = "audio";


    // The units for the below four KEYs currently vary.
    // The preferred units of measure are specified in the comment for each.
    /**
     * 定义电力系统相关的关键字常量
     * 用于标识电力系统的电压参数
     */
    public static final String KEY_POWER = "power"; // V

    /**
     * 定义电池系统相关的关键字常量
     * 用于标识电池电压参数
     */
    public static final String KEY_BATTERY = "battery"; // V

    /**
     * 定义电池电量相关的关键字常量
     * 用于标识电池电量百分比参数
     */
    public static final String KEY_BATTERY_LEVEL = "batteryLevel"; // %

    /**
     * 定义燃油系统相关的关键字常量
     * 用于标识燃油容量参数
     */
    public static final String KEY_FUEL = "fuel"; // liters

    /**
     * 定义燃油消耗相关的关键字常量
     * 用于标识已使用燃油量参数
     */
    public static final String KEY_FUEL_USED = "fuelUsed"; // 升

    /**
     * 定义燃油消耗率相关的关键字常量
     * 用于标识燃油消耗速率参数
     */
    public static final String KEY_FUEL_CONSUMPTION = "fuelConsumption"; // 升/小时

    /**
     * 定义燃油液位相关的关键字常量
     * 用于标识燃油液位百分比参数
     */
    public static final String KEY_FUEL_LEVEL = "fuelLevel"; // %


    /** 固件版本号键名 */
    public static final String KEY_VERSION_FW = "versionFw";

    /** 硬件版本号键名 */
    public static final String KEY_VERSION_HW = "versionHw";

    /** 设备类型键名 */
    public static final String KEY_TYPE = "type";

    /** 点火状态键名 */
    public static final String KEY_IGNITION = "ignition";

    /** 标志位集合键名 */
    public static final String KEY_FLAGS = "flags";

    /** 天线状态键名 */
    public static final String KEY_ANTENNA = "antenna";

    /** 充电状态键名 */
    public static final String KEY_CHARGE = "charge";

    /** IP地址键名 */
    public static final String KEY_IP = "ip";

    /** 归档标识键名 */
    public static final String KEY_ARCHIVE = "archive";

    /** 距离（米）键名 */
    public static final String KEY_DISTANCE = "distance"; // meters

    /** 总距离（米）键名 */
    public static final String KEY_TOTAL_DISTANCE = "totalDistance"; // meters

    /** 发动机转速键名 */
    public static final String KEY_RPM = "rpm";

    /** 车辆识别号码键名 */
    public static final String KEY_VIN = "vin";

    /** 近似位置标志键名 */
    public static final String KEY_APPROXIMATE = "approximate";

    /** 油门开度键名 */
    public static final String KEY_THROTTLE = "throttle";

    /** 移动状态键名 */
    public static final String KEY_MOTION = "motion";

    /** 布防状态键名 */
    public static final String KEY_ARMED = "armed";

    /** 电子围栏事件键名 */
    public static final String KEY_GEOFENCE = "geofence";

    /** 加速度数据键名 */
    public static final String KEY_ACCELERATION = "acceleration";

    /** 湿度数据键名 */
    public static final String KEY_HUMIDITY = "humidity";

    /** 设备温度（摄氏度）键名 */
    public static final String KEY_DEVICE_TEMP = "deviceTemp"; // celsius

    /** 冷却液温度（摄氏度）键名 */
    public static final String KEY_COOLANT_TEMP = "coolantTemp"; // celsius

    /** 发动机负载键名 */
    public static final String KEY_ENGINE_LOAD = "engineLoad";

    /** 发动机温度键名 */
    public static final String KEY_ENGINE_TEMP = "engineTemp";

    /** 运营商信息键名 */
    public static final String KEY_OPERATOR = "operator";

    /** 指令内容键名 */
    public static final String KEY_COMMAND = "command";

    /** 阻断状态键名 */
    public static final String KEY_BLOCKED = "blocked";

    /** 锁定状态键名 */
    public static final String KEY_LOCK = "lock";

    /** 车门状态键名 */
    public static final String KEY_DOOR = "door";

    /** 轴重数据键名 */
    public static final String KEY_AXLE_WEIGHT = "axleWeight";

    /** G-Sensor数据键名 */
    public static final String KEY_G_SENSOR = "gSensor";

    /** ICCID卡号键名 */
    public static final String KEY_ICCID = "iccid";

    /** 电话号码键名 */
    public static final String KEY_PHONE = "phone";

    /** 限速值键名 */
    public static final String KEY_SPEED_LIMIT = "speedLimit";

    /** 驾驶时间键名 */
    public static final String KEY_DRIVING_TIME = "drivingTime";

    /**
     * 定义数据传输相关的常量键值
     */
    public static final String KEY_DTCS = "dtcs";

    /**
     * 定义OBD速度相关的常量键值，单位为km/h
     */
    public static final String KEY_OBD_SPEED = "obdSpeed"; // km/h

    /**
     * 定义OBD里程表相关的常量键值，单位为米
     */
    public static final String KEY_OBD_ODOMETER = "obdOdometer"; // meters

    /**
     * 定义结果相关的常量键值
     */
    public static final String KEY_RESULT = "result";

    /**
     * 定义驾驶员唯一标识符相关的常量键值
     */
    public static final String KEY_DRIVER_UNIQUE_ID = "driverUniqueId";

    /**
     * 定义卡片相关的常量键值
     */
    public static final String KEY_CARD = "card";


    // Start with 1 not 0
    /**
     * 温度传感器数据前缀标识符
     * 用于标识与温度相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_TEMP = "temp";

    /**
     * 模数转换器数据前缀标识符
     * 用于标识与模数转换相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_ADC = "adc";

    /**
     * 输入输出端口数据前缀标识符
     * 用于标识与通用输入输出相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_IO = "io";

    /**
     * 计数器数据前缀标识符
     * 用于标识与计数操作相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_COUNT = "count";

    /**
     * 输入信号数据前缀标识符
     * 用于标识与输入信号相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_IN = "in";

    /**
     * 输出信号数据前缀标识符
     * 用于标识与输出信号相关的数据字段或变量名称前缀
     */
    public static final String PREFIX_OUT = "out";

    /** 通用报警 */
    public static final String ALARM_GENERAL = "general";

    /** SOS紧急报警 */
    public static final String ALARM_SOS = "sos";

    /** 振动报警 */
    public static final String ALARM_VIBRATION = "vibration";

    /** 移动报警 */
    public static final String ALARM_MOVEMENT = "movement";

    /** 低速报警 */
    public static final String ALARM_LOW_SPEED = "lowspeed";

    /** 超速报警 */
    public static final String ALARM_OVERSPEED = "overspeed";

    /** 跌倒报警 */
    public static final String ALARM_FALL_DOWN = "fallDown";

    /** 低电量报警 */
    public static final String ALARM_LOW_POWER = "lowPower";

    /** 低电池报警（与lowPower类似，可能用于不同设备） */
    public static final String ALARM_LOW_BATTERY = "lowBattery";

    /** 故障报警 */
    public static final String ALARM_FAULT = "fault";

    /** 设备关机报警 */
    public static final String ALARM_POWER_OFF = "powerOff";

    /** 设备开机报警 */
    public static final String ALARM_POWER_ON = "powerOn";

    /** 车门报警 */
    public static final String ALARM_DOOR = "door";

    /** 上锁报警 */
    public static final String ALARM_LOCK = "lock";

    /** 解锁报警 */
    public static final String ALARM_UNLOCK = "unlock";

    /** 地理围栏报警 */
    public static final String ALARM_GEOFENCE = "geofence";

    /** 进入地理围栏报警 */
    public static final String ALARM_GEOFENCE_ENTER = "geofenceEnter";

    /** 离开地理围栏报警 */
    public static final String ALARM_GEOFENCE_EXIT = "geofenceExit";

    /** GPS天线被切断报警 */
    public static final String ALARM_GPS_ANTENNA_CUT = "gpsAntennaCut";

    /** 事故报警 */
    public static final String ALARM_ACCIDENT = "accident";

    /** 拖车报警 */
    public static final String ALARM_TOW = "tow";

    /** 怠速报警 */
    public static final String ALARM_IDLE = "idle";

    /** 高转速报警 */
    public static final String ALARM_HIGH_RPM = "highRpm";

    /** 急加速报警 */
    public static final String ALARM_ACCELERATION = "hardAcceleration";

    /** 急刹车报警 */
    public static final String ALARM_BRAKING = "hardBraking";

    /** 急转弯报警 */
    public static final String ALARM_CORNERING = "hardCornering";

    /** 变道报警 */
    public static final String ALARM_LANE_CHANGE = "laneChange";

    /** 疲劳驾驶报警 */
    public static final String ALARM_FATIGUE_DRIVING = "fatigueDriving";

    /** 电源切断报警 */
    public static final String ALARM_POWER_CUT = "powerCut";

    /** 电源恢复报警 */
    public static final String ALARM_POWER_RESTORED = "powerRestored";

    /** 信号干扰报警 */
    public static final String ALARM_JAMMING = "jamming";

    /** 温度异常报警 */
    public static final String ALARM_TEMPERATURE = "temperature";

    /** 停车报警 */
    public static final String ALARM_PARKING = "parking";

    /** 引擎盖开启报警 */
    public static final String ALARM_BONNET = "bonnet";

    /** 脚刹报警 */
    public static final String ALARM_FOOT_BRAKE = "footBrake";

    /** 燃油泄漏报警 */
    public static final String ALARM_FUEL_LEAK = "fuelLeak";

    /** 非法操作/篡改报警 */
    public static final String ALARM_TAMPERING = "tampering";

    /** 设备移除报警 */
    public static final String ALARM_REMOVING = "removing";
    private static final Logger LOG = LoggerFactory.getLogger(Position.class);

    public Position() {
    }

    public Position(String protocol) {
        this.protocol = protocol;
    }

    private String protocol;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    private Date serverTime = new Date();

    public Date getServerTime() {
        return serverTime;
    }

    public void setServerTime(Date serverTime) {
        this.serverTime = serverTime;
    }

    private Date deviceTime;

    public Date getDeviceTime() {
        return deviceTime;
    }

    public void setDeviceTime(Date deviceTime) {
        this.deviceTime = deviceTime;
    }

    private Date fixTime;

    public Date getFixTime() {
        return fixTime;
    }

    public void setFixTime(Date fixTime) {
        this.fixTime = fixTime;
    }

    @QueryIgnore
    public void setTime(Date time) {
        setDeviceTime(time);
        setFixTime(time);
    }

    private boolean outdated;

    @JsonIgnore
    @QueryIgnore
    public boolean getOutdated() {
        return outdated;
    }

    @JsonIgnore
    @QueryIgnore
    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
    }

    private boolean valid;

    public boolean getValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    private double latitude;

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude out of range");
        }
        this.latitude = latitude;
    }

    private double longitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude out of range");
        }
        this.longitude = longitude;
    }

    private double altitude; // value in meters

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    private double speed; // value in knots

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    private double course;

    public double getCourse() {
        return course;
    }

    public void setCourse(double course) {
        this.course = course;
    }

    private String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    private double accuracy;

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    private Network network;

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    private List<Long> geofenceIds;

    public List<Long> getGeofenceIds() {
        return geofenceIds;
    }

    public void setGeofenceIds(List<? extends Number> geofenceIds) {
        if (geofenceIds != null) {
            this.geofenceIds = geofenceIds.stream().map(Number::longValue).toList();
        } else {
            this.geofenceIds = null;
        }
    }

    public void addAlarm(String alarm) {
        if (alarm != null) {
            if (hasAttribute(KEY_ALARM)) {
                set(KEY_ALARM, getAttributes().get(KEY_ALARM) + "," + alarm);
            } else {
                set(KEY_ALARM, alarm);
            }
        }
    }

    @JsonIgnore
    @QueryIgnore
    @Override
    public String getType() {
        return super.getType();
    }

    @JsonIgnore
    @QueryIgnore
    @Override
    public void setType(String type) {
        super.setType(type);
    }
 
    private double latitudeWgs84 = 0;
    private double longitudeWgs84 = 0;
    private boolean latFlag = false;
    private boolean lonFlag = false;
 
 
    public double getLatitudeWgs84() {
        return latitudeWgs84;
    }
 
 
    public double getLongitudeWgs84() {
        return longitudeWgs84;
    }
 
 
    public void setLatitudeWgs84(double latitude) {
        this.latitudeWgs84 = latitude;
        this.latFlag = true;
        if (lonFlag) {
            double[] toGcj02 = wgs84ToGcj02(latitudeWgs84, longitudeWgs84);
            setLatitude(toGcj02[0]);
            setLongitude(toGcj02[1]);
            latFlag = false;
            lonFlag = false;
        }
    }
 
 
    public void setLongitudeWgs84(double longitude) {
        this.longitudeWgs84 = longitude;
        this.lonFlag = true;
        if (latFlag) {
            double[] toGcj02 = wgs84ToGcj02(latitudeWgs84, longitudeWgs84);
            setLatitude(toGcj02[0]);
            setLongitude(toGcj02[1]);
            latFlag = false;
            lonFlag = false;
        }
    }
 
    /**
     * 将 WGS84 坐标转换为 GCJ02 坐标
     *
     * @param wgsLat WGS84 坐标系的纬度
     * @param wgsLon WGS84 坐标系的经度
     * @return GCJ02 坐标数组，包含纬度和经度
     */
    private double[] wgs84ToGcj02(double wgsLat, double wgsLon) {
        if (outOfChina(wgsLat, wgsLon)) {
            return new double[]{wgsLat, wgsLon};
        }
        double dLat = transformLat(wgsLon - 105.0, wgsLat - 35.0);
        double dLon = transformLon(wgsLon - 105.0, wgsLat - 35.0);
        double radLat = wgsLat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        double mgLat = wgsLat + dLat;
        double mgLon = wgsLon + dLon;
        LOG.info(String.format("[WGS84](%.6f, %.6f) => [GCJ02](%.6f, %.6f)", wgsLat, wgsLon, mgLat, mgLon));
        return new double[]{mgLat, mgLon};
    }
 
    /**
     * 将 GCJ02 坐标转换为 BD09 坐标
     *
     * @param gcjLat GCJ02 坐标系的纬度
     * @param gcjLon GCJ02 坐标系的经度
     * @return BD09 坐标数组，包含纬度和经度
     */
    private double[] gcj02ToBd09(double gcjLat, double gcjLon) {
        double x = gcjLon, y = gcjLat;
        double z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * X_PI);
        double bdLon = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{bdLat, bdLon};
    }
 
    private boolean outOfChina(double lat, double lon) {
        // 输入验证
        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon)) {
            return true;
        }

        // 中国经纬度边界常量
        final double MIN_CHINA_LON = 73.33;
        final double MAX_CHINA_LON = 135.05;
        final double MIN_CHINA_LAT = 3.51;
        final double MAX_CHINA_LAT = 53.33;

        // 同时判断经度和纬度是否在中国范围内
        return !(lon >= MIN_CHINA_LON && lon <= MAX_CHINA_LON && lat >= MIN_CHINA_LAT && lat <= MAX_CHINA_LAT);
    }

 
    private double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }
 
    private double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
 
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private static final double PI = Math.PI;
    private static final double X_PI = 3.14159265358979324 * 3000.0 / 180.0;

}
