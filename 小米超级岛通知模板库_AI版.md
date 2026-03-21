# **小米超级岛通知模板库 (20260129)**

本文档定义了小米超级岛（Focus Notification / 灵动岛）的通知模板结构、组件定义以及 JSON 数据下发规范。分为\*\*展开态（焦点通知/岛）**和**摘要态（大岛/小岛）\*\*两大部分。

## **第一部分：焦点通知 / 岛 (展开态) 模板说明**

展开态通知由不同的组件拼装而成，适配 OS2、OS3 以及 Flip 外屏。OS3 支持更多新能力并自动兼容 OS2。

### **1\. 模板组合列表**

| 序号 | 模板结构 | 适用场景示例 |
| :---- | :---- | :---- |
| **1** | 文本组件1 \+ 识别图形组件3 | 天气预警 (左侧大文本，右侧大图) |
| **2** | 文本组件2 \+ 识别图形组件1 | 打车状态、预计等待时间 |
| **3** | IM图文组件 \+ 识别图形组件2 | 音视频通话邀请 |
| **4** | 文本组件2 \+ 识别图形组件1 \+ 进度组件1 | 外卖骑手位置、打车距离与进度 |
| **5** | 文本组件1 \+ 识别图形组件1 \+ 进度组件2 | 餐厅排队叫号 |
| **6** | 文本组件2 \+ 识别图形组件1 \+ 进度组件2 | 停车计费、充电进度 |
| **7** | IM图文组件 \+ 识别图形组件1 \+ 进度组件2 | 文件上传/下载进度 |
| **8** | IM图文组件 \+ 识别图形组件1 \+ 按钮组件3 | 本地生活卡券待消费 |
| **9** | 文本组件2 \+ 识别图形组件1 \+ 按钮组件2 | 电影票取票提醒 |
| **10** | 文本组件2 \+ 识别图形组件1 \+ 按钮组件3 | 快递取件码 |
| **11** | 强调图文组件 \+ 识别图形组件1 \+ 按钮组件2 | 运动跑步进度与控制 |
| **12** | IM图文组件 \+ 按钮组件1 | 通话中控制 (挂断/静音) |
| **13** | 强调图文组件 \+ 按钮组件1 | 录音机录音中、倒计时控制 |
| **14-1** | 新图文组件 | 导航提醒 (仅方向和距离) |
| **14-2** | 新图文组件 \+ 倒计时带图组件 | 导航红绿灯倒计时 |
| **15** | 新图文组件 \+ 按钮组件1 | 验证码提取与复制 |
| **16** | 新图文组件 \+ 识别图形组件1 \+ 按钮组件5 | 订单支付倒计时 |
| **17** | 新图文组件 \+ 识别图形组件1 \+ 按钮组件4 | 接收文件/照片确认 |
| **18** | 封面组件 \+ 识别图形组件1 \+ 按钮组件5 | 演唱会抢票提醒 |
| **19** | 文本组件2 \+ 识别图形组件1 \+ 进度组件3 | 流量使用情况 (带节点进度条) |
| **20** | IM图文组件 \+ 进度组件2 | 游戏下载进度 |
| **21** | 新图文组件 \+ 识别图文组件1 \+ 进度组件3 | 洗衣机洗涤状态 |

## **第二部分：全局规范与附录**

### **2.1 文本组件说明**

* **间隔符号 (showDivider, showContentDivider)**：主要文本和次要文本间可控制显隐 | 符号，用于增强视觉区分。  
* **超长截断规则**：  
  * **主要文本**：超长时显示为 ...，截断优先级：补充文本 \-\> 主要文本1 \-\> 主要文本2 \-\> 特殊标签。  
  * **次要文本**：超长时显示为 ...，截断优先级：次要文本1 \-\> 次要文本2 \-\> 功能图标。

### **2.2 进度条绘制规范**

* **前进图形 (picForward)**：素材尺寸 60*47dp，导出不小于 240*188px (4x)，小于 100kb。图形主体置于 44\*39dp 安全区，底边对齐。  
* **中间节点 / 目标点**：素材尺寸 30*47dp，导出不小于 120*188px (4x)。  
  * 到达前：推荐灰色大气泡，接触进度条。  
  * 到达后：推荐主题色小气泡并上移，下方添加圆点，避让进度条以防边界不清。

### **2.3 关于 actionInfo 的说明 (重要)**

使用 actionInfo 传递自定义 Action 行为时：

* 如果 actionIntentType 为 2 (BroadcastReceiver) 或 3 (Service)，**必须**在 AndroidManifest.xml 中显式声明 android:exported="true"，否则无法响应点击。

## **第三部分：展开态组件详细定义 (JSON Payload)**

### **3.1 文本组件1 (baseInfo, type=1)**

用于展示基础的两行文本结构，第一行是大字体主要信息。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int | Y | 固定为 1 |
| title | String | Y | 主要文本1：描述当前状况的关键信息 |
| subTitle | String |  | 主要文本2 |
| extraTitle | String |  | 补充文本 |
| specialTitle | String |  | 特殊标签：需要强调的特殊文字标签 |
| showDivider | boolean |  | 是否显示主要文本间的分割符 |
| showContentDivider | boolean |  | 是否显示主要文本和补充文本的分割符 |
| content | String | Y | 次要文本1：当前状况的前置描述 |
| subContent | String |  | 次要文本2 |
| picFunction | String |  | 功能图标 |
| colorTitle 等 | String |  | 支持对所有文本配置 colorXXX 和 colorXXXDark (深色模式) |
| colorSpecialBg | String |  | 特殊标签的背景色 |

### **3.2 文本组件2 (baseInfo, type=2)**

与文本组件1类似，但排版上更偏向于次要信息的并列。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int | Y | 固定为 2 |
| title, subTitle, extraTitle, specialTitle | String |  | 同文本组件1 |
| content, subContent | String |  | 同文本组件1 |
| showDivider | boolean |  | 是否显示**次要**文本间的分割符 |
| showContentDivider | boolean |  | 是否显示**次要**文本和功能图标的分割符 |

### **3.3 IM图文组件 (chatInfo)**

包含左侧头像/大图标的组件，常用于通讯、下载。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| picProfile | String | Y | 头像类小图资源 |
| picProfileDark | String |  | 深色模式图片 |
| appiconPkg | String |  | 自定义应用图标包名 |
| title | String | Y | 主要文本 |
| content | String | Y | 次要文本 |
| timerInfo | Object | 二选一 | 计时信息（与普通文本二选一），见通用计时对象 |

### **3.4 强调图文组件 (highlightInfo)**

用于突出显示核心数据（如倒计时、大字时间）。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| title | String | Y/二选一 | 强调文本 |
| timerInfo | Object | Y/二选一 | 计时信息 |
| content | String |  | 辅助文本1 (补充信息) |
| subContent | String |  | 辅助文本2 (状态信息) |
| picFunction | String | Y | 描述功能的图标 |
| type | int |  | 1: 隐藏辅助文本1 |

### **3.5 识别图形组件 (picInfo)**

右侧或独立的图标展示区域。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int | Y | 1: appicon; 2: middle; 3: large |
| pic / picDark | String | Y | 图片资源 (系统默认取桌面图标, 若2/3则需要指定pic) |
| actionInfo | Object |  | 点击行为配置 |
| clickWithCollapse | boolean |  | 点击是否收起面板 (Activity类型默认收起) |

### **3.6 倒计时带图组件 (picInfo, type=5)**

专用于红绿灯等需要图标+数字计时的场景。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int | Y | 固定为 5 |
| pic / picDark | String | Y | 状态图标（如红绿灯的红灯） |
| title | String | Y | 组件文字（如计时数字 120） |
| colorTitle | String | Y | 文字颜色，ARGB格式 |

### **3.7 进度组件1 (progressInfo \- 带实体图形)**

用于外卖、打车等具有起点、终点、中间节点和运动实体的进度。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| progress | int | Y | 当前进度百分比 |
| colorProgress / colorProgressEnd | String |  | 进度条起始/结束颜色 |
| picForward | String | Y | 前进图形 (如外卖小哥) |
| picMiddle / picMiddleUnselected | String | Y | 中间节点图标 (通过/未通过) |
| picEnd / picEndUnselected | String | Y | 目标点图标 (通过/未通过) |

### **3.8 进度组件2 (progressInfo \- 纯净版)**

简单的水平进度条。**不传入任何 picXXX 图标字段，即为进度组件2。** 仅需 progress, colorProgress, colorProgressEnd。

### **3.9 进度组件3 (multiProgressInfo \- 带节点/多段)**

用于显示剩余时长、分段任务的进度条。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| title | String | Y | 描述文本 |
| progress | int | Y | 当前进度 |
| color | String |  | 进度条高亮色，默认系统蓝 |
| points | int |  | 节点数量 (0-4个)，均分进度条 |

### **3.10 按钮组件1 (actions)**

支持圆形按钮、进度按钮、文字按钮组合（最多3个）。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| actions | Array | Y | \[actionInfo, actionInfo\] |
| type (内层) | int | Y | 0:普通按钮(默认); 1:进度按钮; 2:文字按钮(仅支持1个且不可混用) |
| action | String | Y/二选一 | 使用系统预设 action (miui.focus.action\_xxx) |
| actionIcon | String | Y/二选一 | 自定义按钮图标 |
| actionTitle | String |  | 按钮文字 |
| actionIntentType | int |  | 1:Activity; 2:Broadcast; 3:Service |
| actionIntent | String |  | 序列化后的 Intent URI (intent:\#Intent;...;end) |
| progressInfo | Object |  | 当 type=1 时必传，包含 progress, colorProgress, isCCW, isAutoProgress |

### **3.11 按钮组件2 & 3 (hintInfo)**

包含信息提示和操作按钮的底栏。Type 1 为特殊标签样式(组件3)，Type 2 为普通小字样式(组件2)。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int | Y | 1: 按钮组件3 (带特殊标签); 2: 按钮组件2 |
| title | String | Y | 主要文本 (小文本1) |
| subTitle | String |  | 主要文本2 (小文本2) |
| content | String | Y | 前置文本 / 特殊标签文本 |
| subContent | String |  | 前置文本2 (仅Type 2有效) |
| picContent | String |  | 特殊标签图标 (仅Type 1有效) |
| actionInfo | Object | Y | 圆头图文按钮配置 |

### **3.12 按钮组件4 (textButton)**

纯文字按钮组合。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| textButton | Array | Y | 包含 1-2 个 actionInfo 对象的数组 |

### **3.13 按钮组件5 (highlightInfoV3)**

包含高亮大字（如价格）、划线价、标签和操作按钮。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| primaryText | String | Y | 高亮大文本 (如 "4899元") |
| secondaryText | String |  | 补充文本 (如 "4999元") |
| showSecondaryLine | boolean |  | 补充文本是否显示删除线 |
| highLightText | String |  | 文字标签 (如 "限时优惠") |
| actionInfo | Object | Y | 圆头图文按钮 |
| xxxColor | String |  | 各文本颜色及暗色配置 |

### **3.14 封面组件 (coverInfo)**

常用于电影票、音乐会等需要展示竖版海报的场景。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| picCover | String | Y | 竖版封面图资源 |
| title | String | Y | 主要文本 |
| content | String | Y | 次要文本1 |
| subContent | String | Y | 次要文本2 |

### **3.15 新图文组件 (iconTextInfo)**

图标位于左侧且垂直居中，右侧为文本结构。

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| animIconInfo | Object | Y | 包含 type (0:静态) 和 src/srcDark |
| title | String | Y | 主要文本 |
| content | String | Y | 次要文本1 |
| subContent | String |  | 次要文本2 |

### **3.16 模板背景 (bgInfo)**

| 元素字段 | 类型 | 必传 | 描述 |
| :---- | :---- | :---- | :---- |
| type | int |  | 1: 全屏(默认); 2: 右侧 |
| picBg | String |  | 背景图资源 |
| colorBg | String |  | 背景色 |

## **第四部分：岛 (摘要态) 模板说明**

摘要态分为**大岛**和**小岛**。大岛容器以摄像头为界分为 A（左侧）、B（右侧）两个区域。

### **4.1 大岛 (A区 \+ B区组合)**

**A区可用组件：**

1. **图文组件1 (imageTextInfoLeft, type=1)**: 包含图标、前置小字、大字、后置小字。  
2. **图文组件5 (imageTextInfoLeft, type=5)**: 仅包含图标和数字/小字。

**B区可用组件：**

1. **空**  
2. **文本组件 (textInfo)**: 前置小字 \+ 大字 \+ 后置小字。  
3. **图文组件2 (imageTextInfoRight, type=2)**: 前置小字 \+ 大字 \+ 后置小字 \+ 最右侧图标。  
4. **图文组件3 (imageTextInfoRight, type=3)**: 大图标 \+ 大字。  
5. **图文组件4 (imageTextInfoRight, type=4)**: 专用充电组件，类似电池图标+进度。  
6. **图文组件6 (imageTextInfoRight, type=6)**: 状态大图 \+ 悬浮数字 (如红绿灯秒数)。  
7. **等宽数字组件 (sameWidthDigitInfo)**: 固定等宽的数字字形，常用于时间展示。  
8. **定宽数字组件 (fixedWidthDigitInfo)**: 限制最大宽度的数字区域，超长变 ...。  
9. **进度文本组件 (progressTextInfo)**: 文本信息结合环形/线性进度指示。  
10. **大图组件 (picInfo)**: 单纯展示宽幅图片或动画。

### **4.2 大岛核心组件数据结构**

#### **imageTextInfoLeft (A区)**

{  
  "imageTextInfoLeft": {  
    "type": 1, // 或 5  
    "picInfo": { "type": 1, "pic": "xxx" },  
    "textInfo": {  
      "frontTitle": "接驾中",  
      "title": "5",  
      "content": "分钟",  
      "narrowFont": false,  
      "showHighlightColor": true  
    }  
  }  
}

#### **imageTextInfoRight (B区)**

{  
  "imageTextInfoRight": {  
    "type": 2, // 2, 3, 4, 6  
    "picInfo": { "type": 1, "pic": "xxx" },  
    "textInfo": {  
      "frontTitle": "充电中",  
      "title": "24%",  
      "content": "剩5分钟",  
      "showHighlightColor": false  
    }  
  }  
}

#### **其他独立B区组件结构简述**

* fixedWidthDigitInfo: { "digit": "1.02", "content": "km", "showHighlightColor": true }  
* sameWidthDigitInfo: { "digit": "06:23", "content": "开场", "timerInfo": {...} }  
* progressTextInfo: { "progressInfo": { "progress": 40, "colorReach": "..." }, "textInfo": {...} }

### **4.3 小岛**

小岛空间极小，展示逻辑按优先级取值：开发者主动上传的图标组件 \-\> 大岛A区左侧图标 \-\> 兜底应用桌面图标。

#### **可用组件：**

1. **图标组件 (picInfo)**: 正方形静态/应用图标，至少 88\*88px。  
2. **图标组合组件 (combinePicInfo)**: 包含 picInfo (中心图标) \+ progressInfo (外圈环形进度)。  
3. **图标文本组件**: 复用大岛 B 区的 imageTextInfoRight (type=6)，用于展示小图+数字（如红绿灯 34秒）。

#### **combinePicInfo 数据结构**

{  
  "combinePicInfo": {  
    "picInfo": {  
      "type": 1,  
      "pic": "miui.land.pic\_xxx"  
    },  
    "progressInfo": {  
      "progress": 60,  
      "colorReach": "\#00FF00",  
      "colorUnReach": "\#333333",  
      "isCCW": false  
    }  
  }  
}  
