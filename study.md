# v2rayNG 项目架构学习笔记

> 最后更新：2026-03-31

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 项目结构](#2-项目结构)
- [3. 技术架构](#3-技术架构)
- [4. 核心模块详解](#4-核心模块详解)
  - [4.1 UI 层 (ui/)](#41-ui-层-ui)
  - [4.2 ViewModel 层 (viewmodel/)](#42-viewmodel-层-viewmodel)
  - [4.3 Handler 层 (handler/)](#43-handler-层-handler)
  - [4.4 Service 层 (service/)](#44-service-层-service)
  - [4.5 DTO 层 (dto/)](#45-dto-层-dto)
  - [4.6 协议格式化 (fmt/)](#46-协议格式化-fmt)
  - [4.7 枚举定义 (enums/)](#47-枚举定义-enums)
  - [4.8 工具类 (util/)](#48-工具类-util)
  - [4.9 辅助类 (helper/)](#49-辅助类-helper)
  - [4.10 广播接收器 (receiver/)](#410-广播接收器-receiver)
  - [4.11 契约 (contracts/)](#411-契约-contracts)
  - [4.12 扩展函数 (extension/)](#412-扩展函数-extension)
- [5. 数据流架构](#5-数据流架构)
- [6. 关键流程详解](#6-关键流程详解)
- [7. 子模块说明](#7-子模块说明)
- [8. 构建与发布](#8-构建与发布)
- [9. 参考资料](#9-参考资料)

---

## 1. 项目概述

v2rayNG 是一个基于 [xray-core](https://github.com/XTLS/Xray-core) 的 Android 客户端，支持多种代理协议（VMess、VLESS、Shadowsocks、Trojan、WireGuard、Hysteria2 等），提供 VPN 和仅代理两种运行模式。

- **语言**：Kotlin
- **架构**：MVVM (Model-View-ViewModel)
- **最低 SDK**：API 24 (Android 7.0)
- **目标 SDK**：API 36
- **存储方案**：MMKV（高性能键值存储）
- **构建工具**：Gradle + Version Catalog (libs.versions.toml)
- **CI/CD**：GitHub Actions（打 tag 自动构建发布）

---

## 2. 项目结构

```
v2rayNG/
├── V2rayNG/                          # 主应用模块
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/v2ray/ang/    # 核心代码（Kotlin）
│   │   │   ├── res/                   # 资源文件
│   │   │   │   ├── layout/            # 布局文件
│   │   │   │   ├── menu/              # 菜单定义
│   │   │   │   ├── values/            # 字符串、颜色、尺寸
│   │   │   │   ├── values-zh-rCN/     # 中文字符串
│   │   │   │   ├── values-zh-rTW/     # 繁中字符串
│   │   │   │   ├── drawable/          # 图片/矢量图
│   │   │   │   ├── xml/               # XML 配置
│   │   │   │   ├── font/              # 字体文件
│   │   │   │   └── mipmap-*/          # 启动图标
│   │   │   ├── assets/                # 原始资源
│   │   │   └── fdroid/res/            # F-Droid 渠道专属资源
│   │   └── build.gradle.kts           # 应用构建配置
│   └── build.gradle.kts               # 根构建配置
├── AndroidLibXrayLite/                # xray-core Go 绑定子模块
│   ├── libv2ray_main.go               # Go JNI 入口
│   └── assets/                        # geoip.dat, geosite.dat
├── hev-socks5-tunnel/                 # HEV SOCKS5 隧道子模块（C 语言）
│   └── src/core/                      # 核心实现
├── compile-hevtun.sh                  # 编译 HEV 隧道脚本
├── .github/workflows/                 # GitHub Actions
│   ├── build.yml                      # 构建 APK & 发布 Release
│   └── fastlane.yml                   # Fastlane 元数据校验
└── gradle/
    └── libs.versions.toml             # 版本目录（统一依赖管理）
```

---

## 3. 技术架构

### 3.1 MVVM 分层

```
┌─────────────────────────────────────────────────────┐
│                    View (UI)                         │
│  Activity / Fragment / Adapter                       │
│  观察 LiveData，处理用户交互                           │
├─────────────────────────────────────────────────────┤
│                 ViewModel                            │
│  持有 UI 状态（LiveData），调用 Handler 层              │
│  不持有 Activity/View 引用                            │
├─────────────────────────────────────────────────────┤
│              Handler / Service                       │
│  业务逻辑层：配置管理、VPN 控制、测试、存储              │
├─────────────────────────────────────────────────────┤
│              Data / Native                           │
│  MMKV 存储 / xray-core native / JNI                  │
└─────────────────────────────────────────────────────┘
```

### 3.2 Product Flavors

| Flavor | 用途 | applicationId |
|--------|------|---------------|
| `playstore` | Google Play 发布 | `com.v2ray.ang` |
| `fdroid` | F-Droid 发布 | `com.v2ray.ang.fdroid` |

### 3.3 代理模式

| 模式 | Service | 说明 |
|------|---------|------|
| VPN | `V2RayVpnService` | 系统级 VPN，所有流量通过 V2Ray |
| 仅代理 | `V2RayProxyOnlyService` | 本地 SOCKS5/HTTP 代理，需手动配置 |
| HEV TUN | `TProxyService` | 使用 hev-socks5-tunnel 实现 TUN |

### 3.4 支持的协议

| 协议 | 枚举值 | 格式化器 |
|------|--------|----------|
| VMess | 1 | `VmessFmt.kt` |
| 自定义 | 2 | `CustomFmt.kt` |
| Shadowsocks | 3 | `ShadowsocksFmt.kt` |
| SOCKS | 4 | `SocksFmt.kt` |
| VLESS | 5 | `VlessFmt.kt` |
| Trojan | 6 | `TrojanFmt.kt` |
| WireGuard | 7 | `WireguardFmt.kt` |
| Hysteria2 | 9 | `Hysteria2Fmt.kt` |
| HTTP | 10 | `HttpFmt.kt` |
| 策略组 | 101 | - |

---

## 4. 核心模块详解

### 4.1 UI 层 (ui/)

#### Activity 继承关系

```
AppCompatActivity
  └── BaseActivity                    # 公共基类：ProgressBar、Toolbar、语言包装
        └── HelperBaseActivity        # 扩展：文件选择、权限、扫码
              ├── MainActivity        # 主界面
              ├── SettingsActivity    # 设置
              ├── SubSettingActivity  # 订阅管理
              ├── ServerActivity      # 服务器编辑
              ├── ServerGroupActivity # 策略组编辑
              └── ...其他 Activity
```

#### 核心页面

| Activity/Fragment | 布局文件 | 职责 |
|-------------------|----------|------|
| `MainActivity` | `activity_main.xml` | 主界面：Tab 分组 + 服务器列表 + FAB 开关 + 导航抽屉 |
| `GroupServerFragment` | `fragment_group_server.xml` | 分组服务器列表，处理点击选中、滑动删除、长按菜单 |
| `ServerActivity` | `activity_server.xml` | 服务器配置编辑（支持所有协议） |
| `ServerCustomConfigActivity` | `activity_scanner.xml` | 自定义 V2Ray JSON 配置编辑器 |
| `ServerGroupActivity` | `activity_server_group.xml` | 策略组编辑（负载均衡类型：最低延迟/随机/轮询） |
| `SettingsActivity` | - | 偏好设置页面（首选项 XML 驱动） |

#### Adapter

| Adapter | 用途 | 特殊功能 |
|---------|------|----------|
| `MainRecyclerAdapter` | 服务器列表 | 显示选中状态、测试延迟、协议图标；支持 ItemTouchHelper 拖拽排序 |
| `GroupPagerAdapter` | ViewPager2 分组标签 | FragmentStateAdapter，管理多个 GroupServerFragment |
| `SubSettingRecyclerAdapter` | 订阅列表 | 长按删除、编辑 |
| `PerAppProxyAdapter` | 分应用代理 | 搜索过滤、全选/反选 |

#### 主界面布局结构 (`activity_main.xml`)

```
DrawerLayout
  ├── NavigationView          # 侧边导航（订阅/路由/设置/日志/备份等）
  └── ConstraintLayout        # 主内容区
      ├── Toolbar + ProgressBar
      ├── TabLayout           # 订阅分组标签
      ├── ViewPager2          # 服务器列表
      ├── LinearLayout        # 测试状态 + FAB 按钮区域
      └── FloatingActionButton # VPN 开关
```

---

### 4.2 ViewModel 层 (viewmodel/)

| ViewModel | 对应页面 | 关键 LiveData | 关键方法 |
|-----------|---------|---------------|----------|
| `MainViewModel` | MainActivity | `isRunning`, `updateListAction`, `updateTestResultAction`, `testsFinishedAction` | `reloadServerList()`, `testAllRealPing()`, `testAllTcping()`, `sortByTestResults()`, `updateConfigViaSubAll()` |
| `SubscriptionsViewModel` | SubSettingActivity | `subscriptionsLiveData` | `loadSubscriptions()`, `addSubscription()`, `deleteSubscription()` |
| `RoutingSettingsViewModel` | RoutingSettingActivity | `routingRulesetsLiveData` | `loadRulesets()`, `importPreset()` |
| `PerAppProxyViewModel` | PerAppProxyActivity | `appInfoLiveData` | `loadApps()` |
| `UserAssetViewModel` | UserAssetActivity | `assetsLiveData` | `loadAssets()` |
| `LogcatViewModel` | LogcatActivity | `logLines` | `startCapture()` |

#### MainViewModel 数据流

```
用户操作
  │
  ▼
MainActivity.onOptionsItemSelected()
  │
  ▼
MainViewModel.testAllRealPing()     ─→ MessageUtil.sendMsg2TestService()
MainViewModel.updateConfigViaSubAll() ─→ AngConfigManager.updateConfigViaSub()
MainViewModel.sortByTestResults()   ─→ MmkvManager.encodeServerList()
MainViewModel.reloadServerList()    ─→ updateCache() → updateListAction.value = -1
  │
  ▼
V2RayTestService (独立 Service 进程)
  │
  ▼ RealPingWorkerService → 并发测试 → MessageUtil.sendMsg2UI()
  │
  ▼
MainViewModel.mMsgReceiver (BroadcastReceiver)
  │ MSG_MEASURE_CONFIG_SUCCESS → 更新延迟缓存 + updateListAction
  │ MSG_MEASURE_CONFIG_NOTIFY  → updateTestResultAction (进度)
  │ MSG_MEASURE_CONFIG_FINISH  → onTestsFinished()
  │
  ▼
MainActivity 观察 LiveData → 更新 UI
```

---

### 4.3 Handler 层 (handler/)

#### 核心数据管理

| Handler | 职责 | 数据源 |
|---------|------|--------|
| `MmkvManager` | 所有持久化数据读写 | MMKV |
| `SettingsManager` | 应用设置读写 + 初始化 | MMKV |
| `V2rayConfigManager` | 生成 V2Ray JSON 配置 | ProfileItem → V2rayConfig |
| `AngConfigManager` | 配置导入/导出/订阅更新 | URL/剪贴板/二维码 |

#### MmkvManager 存储结构

```
MMKV 存储
├── server_[guid]           → 服务器配置（ProfileItem JSON）
├── sub_[guid]              → 订阅配置（SubscriptionItem JSON）
├── sub_group_[guid]        → 订阅组下的服务器列表
├── selected_server         → 当前选中服务器 GUID
├── settings_*              → 应用设置
├── server_affiliation_info_[guid] → 服务器附属信息（测试延迟）
└── routing_rulesets        → 路由规则集列表
```

#### VPN 服务控制

| Handler | 职责 |
|---------|------|
| `V2RayServiceManager` | 启停 V2Ray VPN 服务（`startVService`/`stopVService`），管理 xray-core 进程 |
| `V2RayNativeManager` | JNI 层封装：初始化核心环境、创建核心控制器、测量延迟 |

#### 测试管理

| Handler | 职责 |
|---------|------|
| `SpeedtestManager` | TCPing（Java Socket 实现）、连接测试、IP 信息获取 |
| `RealPingWorkerService` | 真连接延迟测试（调用 native `measureOutboundDelay`），并发控制 + 早停机制 |

#### 其他 Handler

| Handler | 职责 |
|---------|------|
| `NotificationManager` | 常驻通知、速度显示通知 |
| `SubscriptionUpdater` | WorkManager 定时自动更新订阅 |
| `UpdateCheckerManager` | 检查 GitHub Release 新版本 |
| `WebDavManager` | WebDAV 云端备份/恢复 |
| `SettingsChangeManager` | 追踪设置变更，决定是否需要重启服务 |

---

### 4.4 Service 层 (service/)

#### VPN/代理服务

| Service | 说明 |
|---------|------|
| `V2RayVpnService` | 继承 `VpnService`，创建 VPN 接口（TUN），配置路由（绕过局域网等），启动 xray-core 进程 |
| `V2RayProxyOnlyService` | 不创建 VPN 接口，仅启动 xray-core 的 SOCKS5/HTTP 入站 |
| `TProxyService` | 使用 hev-socks5-tunnel 实现 TUN 透明代理 |
| `ProcessService` | 管理 xray-core 子进程生命周期 |

#### 测试服务

| Service | 说明 |
|---------|------|
| `V2RayTestService` | 运行在独立进程，接收测试请求，管理 RealPingWorkerService 生命周期 |
| `RealPingWorkerService` | 并发测试工作器：Semaphore(16) 控制并发，AtomicBoolean earlyStop 实现早停 |

#### 其他服务

| Service | 说明 |
|---------|------|
| `QSTileService` | Android 快捷设置 Tile，控制 VPN 开关 |
| `RealPingWorkerService` | 延迟测试工作器（详见表格下方） |

#### RealPingWorkerService 早停机制

```
所有任务并发启动（Semaphore 限制 16 个同时执行）
  │
  ├── 任务完成 → 检查延迟 < 300ms → fastCount++
  │                                    │
  │                          fastCount >= 20 → earlyStop = true
  │                                    │
  ├── 未开始的任务 → 检查 earlyStop → true 则静默跳过
  │
  └── 正在执行的任务（≤16个）→ 跑完后自然结束
        │
        ▼ joinAll 等待完毕 → onFinish("0")
        │
        ▼ MSG_MEASURE_CONFIG_FINISH → onTestsFinished() → 排序/刷新
```

---

### 4.5 DTO 层 (dto/)

| 类名 | 职责 | 关键字段 |
|------|------|----------|
| `ProfileItem` | 服务器配置模型 | `configType`, `remarks`, `server`, `serverPort`, `security`, `network`, `streamSecurity`, `sni`, `flow` 等 |
| `V2rayConfig` | V2Ray JSON 配置 | `inbounds`, `outbounds`, `routing`, `dns` |
| `V2rayConfig.OutboundBean` | 单个出站配置 | `protocol`, `settings`, `streamSettings` |
| `V2rayConfig.StreamSettingsBean` | 传输层配置 | `network`, `security`, `tlsSettings`, `wsSettings`, `grpcSettings` |
| `SubscriptionItem` | 订阅 | `remarks`, `enabled`, `url`, `userAgent` |
| `SubscriptionCache` | 订阅缓存 | `guid`, `subscription` |
| `SubscriptionUpdateResult` | 订阅更新结果 | `configCount`, `successCount`, `failureCount`, `skipCount` |
| `ServersCache` | 服务器缓存 | `guid`, `profile` |
| `GroupMapItem` | 分组映射 | `id`, `remarks` |
| `TestServiceMessage` | 测试消息 | `key`, `subscriptionId`, `serverGuids` |
| `ServerAffiliationInfo` | 服务器附属信息 | `subscriptionId`, `testDelayMillis` |

---

### 4.6 协议格式化 (fmt/)

负责解析 URI 格式的节点链接和生成 V2Ray Outbound 配置：

```
vmess://base64...     → VmessFmt.parse()   → V2rayConfig.OutboundBean
vless://uuid@host:port → VlessFmt.parse()  → V2rayConfig.OutboundBean
ss://base64...        → ShadowsocksFmt.parse()
trojan://password@host → TrojanFmt.parse()
socks5://...          → SocksFmt.parse()
hysteria2://...       → Hysteria2Fmt.parse()
wg://...              → WireguardFmt.parse()
http://...            → HttpFmt.parse()
```

`FmtBase` 基类提供公共方法：
- `toOutbound()` → 生成 Outbound 配置
- `resolveTransport()` → 解析传输层（WS/H2/GRPC/KCP 等）
- `populateTlsSettings()` → 填充 TLS/Reality 设置

---

### 4.7 枚举定义 (enums/)

| 枚举 | 值 | 用途 |
|------|-----|------|
| `EConfigType` | VMESS(1), CUSTOM(2), SS(3), SOCKS(4), VLESS(5), TROJAN(6), WG(7), HY2(9), HTTP(10), POLICYGROUP(101) | 区分节点类型 |
| `NetworkType` | TCP, KCP, WS, H2, HTTP, GRPC, HTTP_UPGRADE, XHTTP, HYSTERIA | 传输协议 |
| `RoutingType` | GLOBAL, BYPASS_LAN, BYPASS_MAINLAND, BYPASS_CHINA_IP, BYPASS_BT | 路由策略 |
| `Language` | AUTO, ENGLISH, CHINA, TRADITIONAL_CHINESE, ... | 多语言 |

---

### 4.8 工具类 (util/)

| 类名 | 职责 |
|------|------|
| `MessageUtil` | 进程间通信：Activity ↔ Service ↔ TestService 之间发送广播消息 |
| `Utils` | 通用工具：打开 URL、获取剪贴板、暗黑模式、Receiver flags |
| `JsonUtil` | JSON 序列化/反序列化（Gson） |
| `HttpUtil` | HTTP 请求工具 |
| `ZipUtil` | ZIP 压缩/解压 |
| `QRCodeDecoder` | 二维码解码 |
| `AppManagerUtil` | 获取已安装应用列表 |
| `MyContextWrapper` | 语言环境包装（运行时切换语言） |

#### MessageUtil 通信机制

```
Activity                    V2RayService                 V2RayTestService
   │                            │                              │
   │── MSG_REGISTER_CLIENT ────▶│                              │
   │◀── MSG_STATE_RUNNING ──────│                              │
   │◀── MSG_MEASURE_DELAY ──────│                              │
   │                            │── MSG_MEASURE_CONFIG ────────▶│
   │                            │                              │── 测试
   │◀── MSG_MEASURE_CONFIG_SUCCESS ─────────────────────────────│
   │◀── MSG_MEASURE_CONFIG_NOTIFY ──────────────────────────────│
   │◀── MSG_MEASURE_CONFIG_FINISH ──────────────────────────────│
   │                            │── MSG_MEASURE_DELAY ─────────▶│
   │                            │◀── MSG_MEASURE_DELAY_SUCCESS ─│
```

---

### 4.9 辅助类 (helper/)

| 类名 | 职责 |
|------|------|
| `MmkvPreferenceDataStore` | MMKV 实现 Jetpack Preference DataStore 接口（SettingsActivity 首选项自动持久化） |
| `PermissionHelper` | 运行时权限请求封装 |
| `FileChooserHelper` | 系统文件选择器封装 |
| `QRCodeScannerHelper` | 二维码扫描封装 |
| `CustomDividerItemDecoration` | 自定义 RecyclerView 分割线 |
| `SimpleItemTouchHelperCallback` | RecyclerView 拖拽排序回调 |
| `ItemTouchHelperAdapter` | 拖拽排序接口 |
| `ItemTouchHelperViewHolder` | 拖拽排序 ViewHolder 接口 |

---

### 4.10 广播接收器 (receiver/)

| 类名 | 职责 |
|------|------|
| `BootReceiver` | 开机自启（如果设置了自动连接） |
| `TaskerReceiver` | Tasker 插件集成，接收 Tasker 发送的广播 |
| `WidgetProvider` | 桌面小组件（VPN 开关） |

---

### 4.11 契约 (contracts/)

| 契约 | 用途 |
|------|------|
| `ServiceControl` | Activity ↔ Service 控制交互 |
| `Tun2SocksControl` | HEV TUN 控制 |
| `BaseAdapterListener` | Adapter 通用监听接口 |

---

### 4.12 扩展函数 (extension/)

提供 Kotlin 扩展函数：
- `Context.toast()` / `Context.toastError()` / `Context.toastSuccess()` — Toast 简化
- `Intent.serializable()` — 获取可序列化 extras（兼容 API 33+）
- `String.matchesPattern()` — 正则匹配

---

## 5. 数据流架构

### 5.1 服务器列表加载流程

```
MainActivity.onCreate()
  │
  ▼
setupGroupTab()
  │
  ▼
MainViewModel.getSubscriptions()
  │ → MmkvManager.decodeSubscriptions()
  │ → 返回 List<GroupMapItem>
  │
  ▼
GroupPagerAdapter.update(groups) → TabLayoutMediator
  │
  ▼
ViewPager2 页面切换 → subscriptionIdChanged(id)
  │
  ▼
MainViewModel.reloadServerList()
  │ → MmkvManager.decodeServerList(subscriptionId)
  │ → updateCache() → 逐条解码 ProfileItem → serversCache
  │ → updateListAction.value = -1
  │
  ▼
GroupServerFragment 观察 updateListAction → adapter.notifyDataSetChanged()
```

### 5.2 VPN 连接启动流程

```
用户点击 FAB
  │
  ▼
handleFabAction()
  │
  ├── isRunning? → V2RayServiceManager.stopVService()
  │
  └── not running?
       ├── VPN 模式? → VpnService.prepare() → startV2Ray()
       │                                    │
       │                                    ▼
       │                              V2RayServiceManager.startVService()
       │                                    │
       │                                    ▼
       │                              Intent → V2RayVpnService
       │                                    │
       │                                    ▼
       │                              V2rayConfigManager.getV2rayConfig()
       │                                    │
       │                                    ▼
       │                              V2RayNativeManager.newCoreController()
       │                              → startCoreLoop()（启动 xray-core 进程）
       │
       └── 代理模式? → 直接 startV2Ray()（走 ProxyOnlyService）
```

### 5.3 配置生成流程

```
ProfileItem（用户配置）
  │
  ▼
V2rayConfigManager.getV2rayConfig(guid)
  │
  ├── convertProfile2Outbound(profile) → OutboundBean
  │     ├── 根据协议类型选择配置模板
  │     ├── populateTransportSettings() → 填充传输层
  │     │     ├── TCP: 直接
  │     │     ├── WS: wsSettings + tlsSettings
  │     │     ├── GRPC: grpcSettings
  │     │     ├── H2: httpSettings
  │     │     └── XHTTP: xhttpSettings
  │     └── populateTlsSettings() → 填充 TLS/Reality/ECH
  │
  ├── 构建 Inbounds（SOCKS5 + HTTP 入站）
  ├── 构建 Routing（路由规则）
  ├── 构建 DNS（远程/本地 DNS）
  │
  ▼
V2rayConfig（完整 JSON 配置）→ 传给 xray-core
```

---

## 6. 关键流程详解

### 6.1 订阅更新 + 测试 + 排序（一键操作）

```
用户点击 "更新订阅并测试排序"
  │
  ▼
updateSubscriptionThenTestAndSort()
  │
  ├── Step 1: 更新订阅
  │     mainViewModel.updateConfigViaSubAll()
  │     → AngConfigManager.updateConfigViaSub() [每个订阅]
  │     → HTTP 请求订阅 URL → 解析节点 → 保存到 MMKV
  │     → delay(500ms) → reloadServerList()
  │
  ├── Step 2: 测试真连接
  │     设置 onTestsFinishedCallback
  │     mainViewModel.testAllRealPing()
  │     → V2RayTestService → RealPingWorkerService
  │     → 16 并发测试，找到 20 个 <300ms 快节点后提前停止
  │     → 每完成一个发送 MSG_MEASURE_CONFIG_NOTIFY（进度：done/total/fast）
  │
  └── Step 3: 排序 + 选中最快节点
        onTestsFinishedCallback 触发
        → sortByTestResults()（按延迟升序重排 serverList）
        → reloadServerList()
        → setSelectServer(第一个 GUID)（延迟最低）
        → hideLoading() + toast 完成
```

### 6.2 导入节点配置

```
来源：二维码 / 剪贴板 / 本地文件 / 手动输入
  │
  ▼
importBatchConfig(server)
  │
  ▼
AngConfigManager.importBatchConfig()
  │
  ├── 按行分割 → 识别协议前缀
  ├── vmess:// → VmessFmt.parse()
  ├── vless:// → VlessFmt.parse()
  ├── ss://    → ShadowsocksFmt.parse()
  ├── trojan:// → TrojanFmt.parse()
  ├── ...
  │
  ▼
MmkvManager.encodeServerConfig() → 保存到 MMKV
MmkvManager.encodeServerList()    → 添加到对应订阅的列表
```

---

## 7. 子模块说明

### 7.1 AndroidLibXrayLite

- **语言**：Go（通过 `gomobile` 编译为 AAR）
- **功能**：xray-core 的 Android JNI 绑定
- **核心文件**：`libv2ray_main.go` — 导出 `initCoreEnv()`、`newCoreController()`、`measureOutboundDelay()` 等函数
- **资产**：`geoip.dat`（IP 数据库）、`geosite.dat`（域名分类数据库）
- **构建产物**：`libv2ray.aar`（包含 `.so` 文件：arm64-v8a、armeabi-v7a、x86、x86_64）

> 参考：[2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)

### 7.2 hev-socks5-tunnel

- **语言**：C
- **功能**：高性能 SOCKS5 透明代理隧道，作为 TUN 模式的替代方案
- **编译**：`compile-hevtun.sh` 使用 NDK 交叉编译为各架构 `.so`
- **产物**：`libhevtun.so`

> 参考：[rukenshia/hev-socks5-tunnel](https://github.com/rukenshia/hev-socks5-tunnel)

---

## 8. 构建与发布

### 8.1 本地构建

```bash
cd V2rayNG
./gradlew assemblePlaystoreRelease
# 或只构建 arm64-v8a 架构
./gradlew assemblePlaystoreRelease -PABI_FILTERS=arm64-v8a
```

### 8.2 CI/CD（GitHub Actions）

**触发条件**：
- `push tag v*.*.*` → 构建签名 APK → 创建 GitHub Release
- `PR to master` → 仅 Lint 检查

**构建流程**：
1. Checkout（含子模块）
2. 安装 Android SDK + NDK
3. 编译 libhevtun（有缓存）
4. 下载 libv2ray.aar（从上游 Release）
5. 解码签名 Keystore
6. Gradle 构建 APK
7. 整理产物 + 生成 Changelog
8. 发布 GitHub Release

**发布命令**：
```bash
git tag v1.0.0 && git push origin v1.0.0
```

### 8.3 多语言支持

```
values/           → 英文（默认）
values-zh-rCN/    → 简体中文
values-zh-rTW/    → 繁体中文
values-ar/        → 阿拉伯语
values-bn/        → 孟加拉语
values-fa/        → 波斯语
values-ru/        → 俄语
values-vi/        → 越南语
values-bqi-rIR/   → 巴赫蒂亚里语
```

---

## 9. 参考资料

### 官方仓库
- [v2rayNG](https://github.com/2dust/v2rayNG) — 本项目 fork 的上游仓库
- [Xray-core](https://github.com/XTLS/Xray-core) — 核心代理引擎
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) — xray-core Android 绑定

### 协议文档
- [VMess 协议](https://www.v2fly.org/en_US/protocol/vmess.html)
- [VLESS 协议](https://github.com/XTLS/Xray-core/discussions/716)
- [Shadowsocks 协议](https://shadowsocks.org/en/wiki/Protocol.html)
- [Trojan 协议](https://trojan-gfw.github.io/trojan/protocol.html)
- [WireGuard 协议](https://www.wireguard.com/protocol/)
- [Hysteria2 协议](https://v2.hysteria.network/zh/docs/protocol/)

### Android 开发
- [MVVM 架构指南](https://developer.android.com/topic/architecture)
- [Jetpack ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [LiveData](https://developer.android.com/topic/libraries/architecture/livedata)
- [VpnService](https://developer.android.com/reference/android/net/VpnService)
- [MMKV](https://github.com/Tencent/MMKV) — 高性能键值存储

### 构建工具
- [Gradle Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) — 统一依赖版本管理
- [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) — Go → Android 绑定工具
- [Android NDK](https://developer.android.com/ndk/guides) — C/C++ 原生开发

