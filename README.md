### 核心库功能介绍
1. 主要核心包 (org.traccar)
   这是 Traccar 系统的核心包，包含了系统的基础架构和主要组件：
   - 基础协议处理：BaseProtocol, BaseProtocolDecoder, BaseProtocolEncoder 等类提供了协议处理的基础框架
   - 网络通信：TrackerServer, TrackerClient, NetworkMessage 等类处理网络连接和消息传输
   - 主程序入口：Main.java 是系统启动的主类
   - 管道处理：PipelineBuilder, BasePipelineFactory 等负责处理数据流的管道构建
2. API 接口包 (org.traccar.api)
   负责提供 RESTful API 接口：
   - 资源管理：resource 目录下包含各种 REST 资源，如设备、位置、用户等
   - 安全认证：security 目录处理用户认证和权限控制
   - Web Socket支持：AsyncSocket 提供实时通信支持
3. 数据处理与存储 (org.traccar.handler)
   包含各种数据处理器，按顺序处理位置信息：
   - 事件处理：events 目录包含各种事件处理器，如超速、进入/离开地理围栏等
   - 数据过滤：FilterHandler 过滤无效位置数据
   - 地理编码：GeocoderHandler 将经纬度转换为地址信息
   - 地理围栏：GeofenceHandler 处理地理围栏相关逻辑
   - 网络处理：network 目录包含网络相关处理
4. 数据库相关 (org.traccar.database)
   负责与数据库交互的管理器：
    - 命令管理：CommandsManager 处理设备命令
   - 通知管理：NotificationManager 管理系统通知
   - 设备查询：DeviceLookupService 提供设备信息查询服务
   - 统计管理：StatisticsManager 收集系统统计数据
5. 数据模型 (org.traccar.model)
   定义了系统中使用的所有数据模型：
    - 设备与位置：Device, Position 是核心模型
   - 用户与权限：User, Permission 管理用户和权限
   - 事件与通知：Event, Notification 处理系统事件和通知
   - 地理围栏：Geofence 定义地理围栏区域
6. 协议支持 (org.traccar.protocol)
   这是 Traccar 最大的模块，包含对 600 多种 GPS 设备协议的支持：
    - 每个协议通常包含对应的解码器(Decoder)、编码器(Encoder)和协议定义类
   - 支持从简单的 GPS103 到复杂的 Huabao、Teltonika 等各种协议
   - 涵盖了市面上绝大多数 GPS 追踪设备的通信协议
7. 地理编码 (org.traccar.geocoder)
   提供将经纬度坐标转换为地址信息的服务：
    - 支持多种地理编码服务提供商，如 Google、Bing Maps、Here、Nominatim 等
   - Address 类定义了地址信息的结构
   - JsonGeocoder 提供基于 JSON 的地理编码实现
8. 报表系统 (org.traccar.reports)
   生成各种类型的报表：
    - 行程报表：TripsReportProvider 生成行程分析报表
   - 停留报表：StopsReportProvider 分析设备停留信息
   - 汇总报表：SummaryReportProvider 提供综合统计信息
   - 导出功能：支持 CSV、GPX、KML 等格式导出
9. 其他重要模块
    - 通知系统：org.traccar.notification 处理各种通知发送
   - 短信服务：org.traccar.sms 处理短信发送
   - 邮件服务：org.traccar.mail 处理邮件发送
   - 调度任务：org.traccar.schedule 处理定时任务
   
Traccar 是一个功能完整的 GPS 追踪系统，支持大量设备协议，提供 Web 管理界面，能够实时追踪设备位置、生成历史轨迹、发送报警通知等。每个模块都有明确的职责分工，形成了一个完整的生态系统。

