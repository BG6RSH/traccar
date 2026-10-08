# Traccar 功能定制说明

> 基于 Traccar 6.14.5 版本，新增 **高德轨迹纠偏**、**腾讯地图瓦片**、**地图地点搜索（高德地理编码）** 等定制功能。

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

## 三、地理编码服务

### 3.1 逆地理编码（腾讯地图）

在 `debug.xml` 中配置腾讯地图逆地理编码服务：

```xml
<entry key='geocoder.type'>tencent</entry>
<entry key='geocoder.url'>https://apis.map.qq.com/ws/geocoder/v1</entry>
<entry key='geocoder.key'>你的腾讯地图Key</entry>
<entry key='geocoder.format'>%f %h</entry>
```

### 3.2 地图地点搜索 / 正向地理编码（高德）

**背景：** 主地图右上角的地点搜索组件 `MapGeocoder` 原本直连 `nominatim.openstreetmap.org`。该服务在国内不可达，且组件挂载时**即使关键词为空也会发起请求**，请求挂起约 20 秒（TCP 超时）后以 "Failed to fetch" 弹出红色错误提示——即登录后固定出现的报错弹窗。现改为经后端代理调用高德地理编码 API。

#### 后端修改

| 文件 | 修改内容 |
|------|---------|
| `src/main/java/org/traccar/api/resource/ServerResource.java` | 新增 REST 端点 `GET /api/server/geosearch?q={关键词}`，注入 JAX-RS `Client` |

**端点逻辑：**
- 复用轨迹纠偏的配置项 `amap.webservice.key`（无需新增配置），未配置时抛出异常（返回 500 + 明确文案）
- 后端代理调用高德地理编码 API：`https://restapi.amap.com/v3/geocode/geo`，Key 保留在服务端不暴露给浏览器
- 将高德返回的 `geocodes` 数组包装为前端现有逻辑所需的 GeoJSON `FeatureCollection`：每个 Feature 含 `bbox`（坐标点 ±0.01）、`geometry`（Point）和 `properties`（`place_id`、`display_name` = 高德 `formatted_address`）
- 高德返回 `status != 1`（Key 无效、配额超限等）时抛异常并携带高德的 `info` 错误文案，前端弹窗显示具体原因

#### 前端修改

| 文件 | 修改内容 |
|------|---------|
| `src/map/control/MapGeocoder.jsx` | 数据源改为 `/api/server/geosearch`；空关键词不再发请求；选中结果时坐标转换 |

**关键改动点：**
1. **空查询短路**：关键词为空时直接清空结果并 `return`，从根源消除登录后自动发起的空搜索请求（原 "Failed to fetch" 报错的修复）
2. **请求方式**：`fetch` 改为 `fetchOrThrow`，HTTP 错误时弹出后端返回的错误文本而非 "Failed to fetch"
3. **坐标系处理**：高德返回 GCJ-02 坐标，`onSelect` 先用 `gcoord` 将结果 `bbox` 从 GCJ-02 转为 WGS-84，再经原有 `toMapCoordinates()` 适配当前地图坐标系，保证在任意地图样式下跳转定位均准确

---

## 四、坐标系处理总结

| 数据源 | 原始坐标系 | 地图显示坐标系 | 转换方式 |
|--------|-----------|---------------|---------|
| GPS 设备上报 | WGS-84 | 取决于所选地图 | `toMapCoordinates()` 自动转换 |
| 高德纠偏返回 | GCJ-02 | 取决于所选地图 | `toMapCoordinatesFrom()` 按源坐标系转换 |
| 高德地理编码搜索结果 | GCJ-02 | 取决于所选地图 | 前端先 `gcoord` 转 WGS-84，再 `toMapCoordinates()` |
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
| `src/main/java/org/traccar/api/resource/ServerResource.java` | 修改 | 地图地点搜索 |
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
| `src/map/control/MapGeocoder.jsx` | 修改 | 地图地点搜索 |
| `src/resources/l10n/en.json` | 修改 | 轨迹纠偏 + 腾讯瓦片 |
| `src/resources/l10n/zh.json` | 修改 | 轨迹纠偏 + 腾讯瓦片 |
| `src/login/LoginPage.jsx` | 修改 | 论坛链接 |
| `index.html` | 修改 | 标题后缀 |

---

## 六、Git 版本管理与上游更新流程

> 背景：曾发生过 git 提交覆盖事故（"添加腾讯地图"的提交基于旧快照，把"高德纠偏"代码全部冲掉），导致定制功能丢失且无法从 git 历史恢复。**所有定制必须先提交到自定义分支并推送到私有远程，才有资格谈上游更新。**

### 6.1 仓库与分支结构（2026-08 建立）

两个仓库（后端 `traccar` + 前端子模块 `traccar-web`）采用相同结构：

| 项目 | 后端 | 前端（子模块） |
|------|------|---------------|
| `origin`（官方上游，只 fetch） | `https://github.com/traccar/traccar.git` | `https://github.com/traccar/traccar-web.git` |
| `myfork`（自己的仓库，推送定制） | `https://github.com/BG6RSH/traccar.git` | `https://github.com/BG6RSH/traccar-web.git` |
| 定制分支 | `custom/v6.15`、`custom/v6.16.0` | `custom/v6.15`、`custom/v6.16.0` |
| 基线标签 | `v6.15.2-custom` | `v6.15.2-custom` |

### 6.2 日常修改后提交推送（两个仓库分别执行）

```bash
git add <修改的文件>
git commit -m "说明"
git push                    # custom/v6.15 已跟踪 myfork，直接 push 即可
```

> 新建定制分支（如 `custom/v6.16.0`）首次推送时需指定上游：
> ```bash
> git push -u myfork custom/v6.16.0
> ```
> 后续 `git push` 即可。子模块（traccar-web）需先进子模块目录推送，再回父仓库提交指针更新并推送。

### 6.3 上游发布新版本时的合并流程（以 v6.16.0 为例）

> 整体顺序：**先后端、再前端、最后更新子模块指针并推送**。
> 命令中的 `custom/v6.16.0` 为新版本分支，下次升级到 v6.17.0 时按同样流程替换版本号即可。

#### 步骤 1：后端合并 origin/master

```bash
# 在后端仓库根目录执行
git fetch origin --tags                # 拉取上游新提交和标签，不合并
git checkout custom/v6.15              # 切到当前定制分支
git merge origin/master --no-edit     # 合并上游 master，逐个解决冲突
# 冲突高发点：MainModule.java / PositionResource.java / ServerResource.java
# （多为新增 import 行冲突，保留双方 import 即可）
git commit --no-edit                   # 冲突解决后完成合并提交
```

#### 步骤 2：前端子模块合并 origin/master

```bash
cd traccar-web
# 2.1 暂存可能存在的未提交修改（package-lock.json 等自动生成文件）
git stash push -u -m "WIP before merge"
# 2.2 切到定制分支并创建新版本分支
git checkout custom/v6.15
git checkout -b custom/v6.16.0
# 2.3 合并上游
git merge origin/master --no-edit
```

**冲突解决（按文件类型分别处理）：**

| 冲突文件 | 处理方式 |
|---------|---------|
| `src/resources/l10n/zh.json` | 手工编辑：保留本地定制键（`loginForum`、`mapTencentSatellite`、`mapTencentHybrid`、`reportGraspRoad`）+ 吸收上游文案调整 |
| `package-lock.json` | 取上游版本（自动生成文件，需匹配新 package.json）：`git checkout --theirs package-lock.json && git add package-lock.json` |

```bash
git add src/resources/l10n/zh.json package-lock.json
git commit --no-edit                     # 完成合并提交
cd ..                                    # 回到后端仓库根目录
```

#### 步骤 3：更新后端仓库的子模块指针

```bash
# 在后端仓库根目录
git add traccar-web
git commit -m "Point traccar-web submodule to custom/v6.16.0 branch"
```

#### 步骤 4：合并后修复上游 API 重命名

> 上游 v6.16.0 将 `ErrorHandler` 重构为 `MessageHandler`，`errorsActions` 重命名为 `messagesActions`，
> `src/store/errors.js` 改为 `src/store/messages.js`。定制文件中残留的旧名引用会导致运行时报错：
> `SyntaxError: The requested module '/src/store/index.js' does not provide an export named 'errorsActions'`

```bash
cd traccar-web
# 全仓搜索旧名引用：grep -rn "errorsActions\|ErrorHandler" src/
# 把 src/other/ReplayPage.jsx 中 3 处 errorsActions 改为 messagesActions
git add src/other/ReplayPage.jsx
git commit -m "Fix ReplayPage to use messagesActions after upstream rename"
cd ..
git add traccar-web
git commit -m "Update traccar-web submodule pointer for messagesActions fix"
```

#### 步骤 5：推送到 myfork

```bash
# 顺序重要：先推子模块，再推父仓库（父仓库的子模块指针需指向已推送的子模块提交）
git -C traccar-web push -u myfork custom/v6.16.0
git push -u myfork custom/v6.16.0
```

#### 步骤 6：（可选）打新基线标签

```bash
# 全部测试通过后打标签
git tag v6.16.0-custom
git push myfork v6.16.0-custom
# 子模块也打标签（可选）
git -C traccar-web tag v6.16.0-custom
git -C traccar-web push myfork v6.16.0-custom
```

**冲突解决原则：**
1. 以本文档**第五节修改文件清单**为准，清单内的文件冲突时逐个保留定制逻辑 + 吸收上游新代码（不能图省事全选某一侧）
2. 翻译文件（`l10n/*.json`）冲突建议以上游为基线，重新手工追加自定义键（如 `reportGraspRoad`、`loginForum`、腾讯地图键）
3. 前端合并后，回到后端仓库把子模块指针更新到新提交并提交（`git add traccar-web`）
4. 全部完成后重新编译后端、构建前端，按 1.3 节交互流程验证纠偏等功能正常，再打新标签；出问题可 `git reset --hard` 回滚到上一个 `-custom` 标签
5. 合并提交信息建议注明上游版本，如 `MergeUpstreamV6.16.0IntoCustom`
6. **合并后必做**：grep 搜索上游已重命名/删除的 API（如 `errorsActions`、`ErrorHandler`），把定制文件中的旧名引用全部更新为新名，否则运行时报 `SyntaxError`

### 6.4 危险操作黑名单（定制丢失的主要根源）

| 操作 | 后果 |
|------|------|
| `git submodule update --init` | 把 `traccar-web` 检出到外层记录的（可能是上游）提交，工作区定制从视图消失 |
| `git reset --hard`（未提交定制时） | 未提交的定制直接销毁，无法恢复 |
| `git checkout .` / `git restore .` | 同上 |
| 基于旧快照做新提交再合并 | 上次覆盖事故的根源：旧快照不含新功能的提交会把这些功能全部冲掉 |
| 用上游 release 压缩包直接覆盖目录 | 无冲突提示，定制无声丢失 |
| 把 `debug.xml` 提交到公开仓库 | 含高德/腾讯 API Key，泄露后会被盗用配额 |
| `git push origin`（任何写操作） | 官方上游仓库无写权限且不应写；写操作一律用 `myfork` |

### 6.5 永不提交的文件清单（当前均未纳入版本控制，保持现状）

| 文件/目录 | 仓库 | 原因 |
|-----------|------|------|
| `debug.xml` | 后端 | 含高德/腾讯 API Key |
| `git_list.txt` | 后端 | 临时文件 |
| `src-old/` | 前端 | 旧版代码备份目录，仅本地参考 |

### 6.6 灾备恢复（最坏情况）

```bash
# 本地仓库丢失/损坏时，从自己的仓库重建（替换为当前最新定制分支）
git clone -b custom/v6.16.0 https://github.com/BG6RSH/traccar.git
cd traccar
git submodule update --init      # 检出子模块到外层记录的定制提交后，
                                 # 进 traccar-web 确认/切换到 custom/v6.16.0 分支
git remote add myfork https://github.com/BG6RSH/traccar-web.git   # 子模块内单独加远程便于推送
git remote add origin https://github.com/traccar/traccar.git      # 如缺失，补上游远程便于 fetch 更新
```

> 注意：恢复后需手工重建 `debug.xml`（含 API Key，参考 1.4 节配置说明）。
