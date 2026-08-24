# Traccar 功能定制说明

> 基于 Traccar 6.14.5 版本，新增 **高德轨迹纠偏** 与 **腾讯地图瓦片** 两大功能。

---

## 一、高德轨迹纠偏功能

### 1.1 功能概述

在轨迹回放页面增加"轨迹纠偏"开关，开启后调用高德地图驾车轨迹纠偏 API，将原始 GPS 定位点吸附到道路上，并以虚线叠加显示在原始轨迹之上。

### 1.2 后端修改

#### 新增文件

| 文件 | 说明 |
|------|------|
| `src/main/java/org/traccar/mapmatcher/AmapGraspRoadService.java` | 高德驾车轨迹纠偏服务核心类 |

**核心逻辑：**
- 调用高德 API：`https://restapi.amap.com/v4/grasproad/driving`
- 单次请求上限 **500 个点**，超出自动分批请求
- WGS-84 坐标自动转换为 GCJ-02 后提交
- 返回 `CorrectedPoint` 列表（纬度、经度、时间）
- 包含完整错误处理与日志记录

#### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `src/main/java/org/traccar/config/Keys.java` | 新增配置键 `amap.graspRoad.enable`（开关）和 `amap.webservice.key`（API Key） |
| `src/main/java/org/traccar/MainModule.java` | 新增 Guice Provider 方法，条件性创建 `AmapGraspRoadService` 单例 |
| `src/main/java/org/traccar/api/resource/PositionResource.java` | 新增 REST 端点 `GET /api/positions/corrected`，查询纠偏后的坐标 |

**API 端点详情：**
```
GET /api/positions/corrected?deviceId={id}&from={date}&to={date}
```
- 返回纠偏后的 Position 列表
- 每个 Position 的 `attributes.correctedCoordinateSystem` 值为 `"gcj02"`
- 若服务未启用，返回 `503 Service Unavailable`

### 1.3 前端修改

| 文件 | 修改内容 |
|------|---------|
| `src/other/ReplayPage.jsx` | 新增纠偏开关 UI、状态管理、API 调用逻辑；回放标记按 `fixTime` 二分查找匹配最近纠偏点 |
| `src/map/MapRoutePath.js` | 新增 `lineDash`（虚线样式）、`sourceCoordinateSystem`（源坐标系）、`color`（固定线色）、`lineWidth`/`lineOpacity`（线宽/透明度覆盖，用于纠偏叠加层）属性支持。注意：`line-dasharray` 是 MapLibre 的 **paint 属性**，必须放在 `paint` 中（误放 `layout` 会导致 addLayer 样式校验抛错、图层创建失败而不显示） |
| `src/map/MapRoutePoints.jsx` | 新增 `sourceCoordinateSystem` 属性支持 |
| `src/map/MapMarkers.js` | 新增 `sourceCoordinateSystem` 属性支持（v6.15 上游重构后的标记渲染组件） |
| `src/map/MapPositionMarkers.jsx` | 新增 `sourceCoordinateSystem` 属性透传（上游将原 `MapPositions.js` 重构为此组件） |
| `src/map/core/mapUtil.js` | 新增 `toMapCoordinatesFrom()` 函数，支持从指定坐标系转换到地图坐标系 |
| `src/resources/l10n/en.json` | 新增翻译键 `reportGraspRoad: "Grasp Road"` |
| `src/resources/l10n/zh.json` | 新增翻译键 `reportGraspRoad: "轨迹纠偏"` |

**前端交互流程：**
1. 用户加载轨迹数据后，回放面板显示"轨迹纠偏"开关（仅当前地图为 GCJ-02 坐标系时）。开关可见性由 `mapGcj02` state 驱动：监听地图 `styledata` 事件实时同步 `map.coordinateSystem`，确保切换地图样式后开关立即显示/隐藏（不能直接读 `map.coordinateSystem`，该属性变化不会触发 React 重渲染）
2. 开启开关 → 调用 `/api/positions/corrected` 获取纠偏坐标（仅在无缓存时请求）
3. 纠偏轨迹以 **紫色半透明虚线**（`#831eba`，线宽 5，透明度 0.6，虚线 `[2,4]`，与旧版视觉一致）叠加在原始轨迹（实线打底）之上，回放车辆标记跟随纠偏后的位置移动；原始路由点仍可点击定位（滑块/播放索引始终基于原始数据）
4. 请求失败或返回空数据时，开关保持关闭并弹出错误提示（不静默失败）
5. 纠偏图层以 `key={\`corrected-${mapGcj02}\`}` 挂载，地图坐标系变化时强制重建图层并按新坐标系重新转换坐标；`sourceCoordinateSystem` 缺失时兜底为 `'gcj02'`（高德返回必为 GCJ-02），避免被误当 WGS-84 双重转换偏出视野

**纠偏数据缓存策略：**
- 关闭开关**不清除**已获取的纠偏数据，再次开启直接复用缓存（不重复调用高德 API）
- 重新查询（更换设备/时间范围）时清空缓存，下次开启开关重新请求最新纠偏数据
- 地图切换为**非 GCJ-02 坐标系**时自动清空缓存并关闭开关（避免切换回 GCJ-02 地图后复用陈旧数据）

**非一一对应的处理（重要）：**
- 高德 grasproad API 沿道路重采样，**返回点数量与原始点不相等，两者不能按数组索引一一对应**
- 回放标记取值：根据当前原始点的 `fixTime`，在按时间升序排列的纠偏点序列中**二分查找**时间最近的纠偏点作为标记位置（相邻两点取时间差更小者）
- 纠偏轨迹仅作紫色虚线叠加层展示，不生成可点击路由点（避免点击索引与原始滑块错位）
- 标记与纠偏路径的坐标系均从各自数据的 `attributes.correctedCoordinateSystem`（`gcj02`）派生，经 `toMapCoordinatesFrom()` 转换

### 1.4 配置说明

在 `debug.xml`（或 `traccar.xml`）中添加：

```xml
<!-- 高德轨迹纠偏服务 -->
<entry key='amap.graspRoad.enable'>true</entry>
<entry key='amap.webservice.key'>你的高德Web服务API密钥</entry>
```

> 错误处理：服务未启用时端点返回 `503`；高德 API 调用失败或返回空数据时端点返回 `502`（而非静默返回空数组），前端据此弹出错误提示。

---

## 二、腾讯地图瓦片功能

### 2.1 功能概述

在地图样式列表中新增三种腾讯地图瓦片：
- **腾讯地图**（路网图）
- **腾讯卫星地图**
- **腾讯混合地图**（卫星 + 路网标注叠加）

所有腾讯地图均使用 **GCJ-02 坐标系**（火星坐标），与高德地图坐标系一致。

### 2.2 前端修改（与上游 v6.15 官方腾讯地图支持合并后的最终状态）

> 说明：上游 v6.15 官方已新增腾讯路网地图（`tencentMap`，使用 `tile` 端点 + `scheme: 'tms'` 处理 Y 轴翻转）。
> 合并时以官方实现为主，本地仅保留官方未提供的**卫星**与**混合**地图。
> 经验证 `styleid=3` 瓦片为带透明通道的 PNG（tRNS），可直接作为混合地图的标注叠加层。
> 原先的 `tencent://` 协议（realtimerender 端点）已移除，因其返回不透明 JPEG，会遮盖卫星底图。

#### MapView.jsx — 自定义协议注册

保留一个 MapLibre 自定义协议处理器：

**`tencent-sate://` 协议（卫星瓦片）：**
- 将 MapLibre XYZ 坐标转换为腾讯 TMS 坐标（`yTMS = 2^z - 1 - y`）
- 再转换为腾讯卫星瓦片的嵌套目录路径：
  `https://p{N}.map.gtimg.com/sateTiles/{z}/{x16}/{y16}/{x}_{yTMS}.jpg`
- 其中 `N = x % 4`，`x16 = x / 16`，`y16 = yTMS / 16`

#### useMapStyles.js — 地图样式注册

当前三个腾讯地图样式定义：

| ID | 名称 | 瓦片来源 | 坐标系 | 来源 |
|----|------|---------|--------|------|
| `tencentMap` | 腾讯地图 | `rt{i}.map.gtimg.com/tile?...&styleid=3` + `scheme: 'tms'` | GCJ-02 | 上游官方 |
| `tencentSatellite` | 腾讯卫星地图 | `tencent-sate://tile/{z}/{x}/{y}` | GCJ-02 | 本地定制 |
| `tencentHybrid` | 腾讯混合地图 | 双图层叠加（卫星 + `styleid=3` 标注） | GCJ-02 | 本地定制 |

**混合地图实现：** 使用两个 raster source 叠加：
- 底层：`satellite`（腾讯卫星，`tencent-sate://` 协议）
- 顶层：`annotation`（腾讯路网标注，与官方 `tencentMap` 相同的 URL + `scheme: 'tms'`，透明 PNG）

#### 国际化

| 文件 | 内容 |
|------|------|
| `src/resources/l10n/en.json` | `mapTencentMap`（官方）、`mapTencentSatellite`、`mapTencentHybrid` |
| `src/resources/l10n/zh.json` | 腾讯地图、腾讯卫星、腾讯混合 |

### 2.3 其他前端修改

| 文件 | 修改内容 |
|------|---------|
| `index.html` | 页面标题改为 `<title>${title} 火星坐标版</title>`（`${title}` 由后端 `OverrideTextFilter` 按服务器 `title` 属性动态替换，后缀为静态文本） |
| `src/login/LoginPage.jsx` | 登录页底部新增"访问论坛"链接（`http://bbs.atoo.top:8081`，新窗口打开，与注册/重置密码链接并列） |
| `src/resources/l10n/en.json` | 新增翻译键 `loginForum: "Forum"` |
| `src/resources/l10n/zh.json` | 新增翻译键 `loginForum: "访问论坛"` |

---

## 三、逆地理编码（腾讯地图）

在 `debug.xml` 中配置腾讯地图逆地理编码服务：

```xml
<entry key='geocoder.type'>tencent</entry>
<entry key='geocoder.url'>https://apis.map.qq.com/ws/geocoder/v1</entry>
<entry key='geocoder.key'>你的腾讯地图Key</entry>
<entry key='geocoder.format'>%f %h</entry>
```

---

## 四、坐标系处理总结

| 数据源 | 原始坐标系 | 地图显示坐标系 | 转换方式 |
|--------|-----------|---------------|---------|
| GPS 设备上报 | WGS-84 | 取决于所选地图 | `toMapCoordinates()` 自动转换 |
| 高德纠偏返回 | GCJ-02 | 取决于所选地图 | `toMapCoordinatesFrom()` 按源坐标系转换 |
| 腾讯瓦片 | — | GCJ-02 | 瓦片本身已为 GCJ-02 偏移 |

核心转换函数 `mapUtil.js`：
- `toMapCoordinates(lng, lat)` — 从 WGS-84 转换到当前地图坐标系
- `toMapCoordinatesFrom(lng, lat, sourceCS)` — 从指定坐标系转换到当前地图坐标系
- `fromMapCoordinates(lng, lat)` — 从当前地图坐标系转回 WGS-84

---

## 五、修改文件清单

### 后端（traccar）

| 文件路径 | 操作 | 所属功能 |
|---------|------|---------|
| `src/main/java/org/traccar/mapmatcher/AmapGraspRoadService.java` | 新增 | 轨迹纠偏 |
| `src/main/java/org/traccar/config/Keys.java` | 修改 | 轨迹纠偏 |
| `src/main/java/org/traccar/MainModule.java` | 修改 | 轨迹纠偏 |
| `src/main/java/org/traccar/api/resource/PositionResource.java` | 修改 | 轨迹纠偏 |
| `debug.xml` | 修改 | 轨迹纠偏 + 逆地理编码 |

### 前端（traccar-web）

| 文件路径 | 操作 | 所属功能 |
|---------|------|---------|
| `src/map/core/MapView.jsx` | 修改 | 腾讯瓦片 |
| `src/map/core/useMapStyles.js` | 修改 | 腾讯瓦片 |
| `src/map/core/mapUtil.js` | 修改 | 轨迹纠偏 |
| `src/map/MapRoutePath.js` | 修改 | 轨迹纠偏 |
| `src/map/MapRoutePoints.jsx` | 修改 | 轨迹纠偏 |
| `src/map/MapMarkers.js` | 修改 | 轨迹纠偏 |
| `src/map/MapPositionMarkers.jsx` | 修改 | 轨迹纠偏 |
| `src/other/ReplayPage.jsx` | 修改 | 轨迹纠偏 |
| `src/resources/l10n/en.json` | 修改 | 轨迹纠偏 + 腾讯瓦片 |
| `src/resources/l10n/zh.json` | 修改 | 轨迹纠偏 + 腾讯瓦片 |
| `src/login/LoginPage.jsx` | 修改 | 论坛链接 |
| `index.html` | 修改 | 标题后缀 |
