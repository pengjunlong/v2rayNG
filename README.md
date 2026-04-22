# v2rayNG

---

## 📺 TV 适配改动说明（branch: tv-android6）

> 本分支专为 **Android 6+ 盒子 / TV + 遥控器** 场景深度适配，以下为所有改动点，方便后续维护。

### 一、功能增强

| 功能 | 文件 | 说明 |
|---|---|---|
| 一键更新订阅+测试+排序 | `MainActivity.kt`、`MainViewModel.kt` | 菜单「更新订阅并测试排序」：依次更新订阅→真连接测速（带 Early-Stop：累计 20 个 ≤300ms 节点即停止）→按结果排序→自动选中最优节点→重启 VPN 服务 |
| 测试进度显示 | `MainViewModel.kt`、`strings.xml` | 底部状态栏实时显示「已完成/总数/⚡快节点数」 |
| 退出应用菜单 | `MainActivity.kt`、`menu_main.xml`、`strings.xml` | 溢出菜单新增「退出」，停止 VPN 服务后调用 `finishAffinity()` 彻底关闭进程，避免后台误杀 |
| MENU 键打开侧栏 | `MainActivity.kt` | 遥控器 MENU 键（或红色键）打开/关闭侧滑导航抽屉；BACK 键在抽屉打开时优先关闭抽屉 |
| 防止服务后台被杀 | `V2RayVpnService.kt`、`V2RayProxyOnlyService.kt`、`AndroidManifest.xml` | VPN/Proxy 服务 `onCreate` 申请 `PARTIAL_WAKE_LOCK`，`onDestroy` 释放，防止 TV 盒子切后台后 CPU 休眠导致断连 |

### 二、焦点高亮（遥控器导航视觉反馈）

统一风格：**橙色边框 + 浅橙底色**，与品牌色 `#f97910` 一致。

| 文件 | 作用范围 |
|---|---|
| `drawable/bg_tv_focus.xml` | 通用焦点 selector（列表项、底部状态栏、表单行等） |
| `drawable/bg_toolbar_item_focus.xml` | Toolbar 图标（搜索、加号、三点菜单） |
| `drawable/bg_dialog_button_focus.xml` | AlertDialog 确定/取消按钮 |
| `drawable/bg_nav_item_focus.xml` | 侧栏 NavigationView 菜单项 |
| `drawable/bg_tab_item_focus.xml` | TabLayout 订阅分组 Tab |
| `values/themes.xml` | 注入 `actionBarItemBackground`、`buttonBarButtonStyle` 使 Toolbar 按钮和弹窗按钮全局生效 |
| `values-night/themes.xml` | 同步注入夜间主题，保证深色模式下一致 |

### 三、布局 TV 化改造

**`activity_main.xml`（主界面）**
- FAB 悬浮按钮从 CoordinatorLayout 移入底部固定栏右侧，与测试状态栏同行——遥控器在列表**任意行**按 `→` 即可直达 FAB，无需滚动到底
- RecyclerView 增加 `nextFocusDown`→`layout_test`、`nextFocusRight`→`fab`、`nextFocusUp`→`tab_group`
- TabLayout 增加 `tabBackground`、`tabRippleColor` 焦点高亮，`focusable="true"`

**所有表单/列表页面（统一替换 `selectableItemBackground` 为 `bg_tv_focus`）**
- `layout_address_port.xml`、`layout_transport.xml`、`layout_tls.xml`、`layout_tls_hysteria2.xml`
- `activity_server_vmess/vless/trojan/shadowsocks/socks/hysteria2/wireguard/custom_config.xml`
- `activity_sub_edit.xml`、`activity_routing_edit.xml`、`activity_user_asset_url.xml`
- `activity_routing_setting.xml`、`activity_user_asset.xml`
- `item_recycler_main.xml`（操作按钮行）、`item_recycler_sub_setting.xml`、`item_recycler_routing_setting.xml`、`item_recycler_user_asset.xml`、`item_recycler_bypass_list.xml`

**NavigationView 侧栏**
- `itemBackground` → `bg_nav_item_focus`；图标色改为橙色（`color_fab_active`）

**菜单顺序**
- `menu_main.xml`：「更新订阅并测试排序」移至溢出菜单**第一项**，遥控器打开菜单后无需移动即可选中

### 四、深色 / 大屏适配

| 文件 | 改动 |
|---|---|
| `values-night/colors.xml` | `colorPrimary` 改为 `#1A1A1A`（更深，减少 TV 大屏眩光）；补充 `colorPing`、`colorPingRed`、`colorConfigType` 深色高对比版本 |
| `values-sw720dp/dimens.xml` | 1080p/720p TV（宽≥720dp）自动放大底部栏高度（64→80dp）、图标区域（24→32dp）、间距（16→20dp） |

### 五、自动构建

| 文件 | 说明 |
|---|---|
| `.github/workflows/build.yml` | 仅构建 `arm64-v8a` 架构；Tag 推送时触发 GitHub Release 并自动上传 APK；使用 `playstore` flavor（包名 `com.v2ray.ang`）直接侧载安装 |

---

A V2Ray client for Android, support [Xray core](https://github.com/XTLS/Xray-core) and [v2fly core](https://github.com/v2fly/v2ray-core)

[![API](https://img.shields.io/badge/API-21%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/2dust/v2rayNG)](https://github.com/2dust/v2rayNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/2dust/v2rayng/badge)](https://www.codefactor.io/repository/github/2dust/v2rayng)
[![GitHub Releases](https://img.shields.io/github/downloads/2dust/v2rayNG/latest/total?logo=github)](https://github.com/2dust/v2rayNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/v2rayn)

### Telegram Channel
[github_2dust](https://t.me/github_2dust)

### Usage

#### Geoip and Geosite
- geoip.dat and geosite.dat files are in `Android/data/com.v2ray.ang/files/assets` (path may differ on some Android device)
- download feature will get enhanced version in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (Note it need a working proxy)
- latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [ip list](https://github.com/Loyalsoldier/geoip) can be imported manually
- possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

### More in our [wiki](https://github.com/2dust/v2rayNG/wiki)

### Development guide

Android project under V2rayNG folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.  
The aar can be compiled from the Golang project [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite).
For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

v2rayNG can run on Android Emulators. For WSA, VPN permission need to be granted via
`appops set [package name] ACTIVATE_VPN allow`
