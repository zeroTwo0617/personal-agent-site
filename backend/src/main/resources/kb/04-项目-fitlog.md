# FitLog 训练记录小程序

## 背景与目标
FitLog 是一个个人训练记录微信小程序。我要解决的核心问题是：用结构化方式记录每一次训练的"动作-组数-次数-重量"，并由此衍生出训练计划、数据趋势、打卡日历与身体数据追踪。市面上的健身 App 要么太重、要么强依赖联网账号，我想做一个数据口径严格自洽、纯本地可控的轻量记录工具。技术形态定为微信小程序原生 + 微信云开发 CloudBase，刻意不引入独立后端——降低运维与账号体系成本，让训练数据始终在用户自己的 CloudBase 空间内。

## 我的职责
我独立完成从数据模型、云函数、页面到主题系统与部署规范的全流程设计与实现。CloudBase 是唯一远端数据源，前端用原生 WXML/WXSS/JS 直接读写；纯聚合逻辑抽到 utils/*Data.js 且刻意不依赖 wx，从而能用 Node 跑单测；训练 Agent 通过云函数调用大模型。一人从 0 到可演示。

## 技术选型与架构
- 形态：微信小程序原生（WXML/WXSS/JS）+ 微信云开发 CloudBase（文档数据库 + 云函数），无独立后端、无自建服务器、无图片 CDN。
- 身份与权限：wx.cloud.init({ traceUser: true }) 自动注入 _openid；所有集合权限设为"仅创建者可读写"，页面只允许按当前用户 openid 过滤。
- 数据：CloudBase 文档数据库，核心集合 workouts（训练会话事实表）、sets（组明细表），以及 plans/bodyMetrics/nutritionLogs/dietPlans/users/agentSessions/agentUploads。
- 分层：app.js（云环境初始化 + 全局主题壳）、app.wxss（全局设计 token）、utils/*Data.js（纯聚合逻辑，不依赖 wx）、pages/*（查询 + 渲染）、cloudfunctions/*（服务端权限 / 批处理 / LLM 调用）。
- 关键决策：纯逻辑放 utils 而非页面，便于 Node 单测且保证多页面口径一致；动作库用内置文字动作库，不依赖 GIF/图片 CDN，零版权与加载成本。

## 核心难点与解决方案
1. 数据口径自洽：定义"唯一数据口径"——训练日只由 workouts 决定；指标（训练量/最大重量/1RM）由 workouts + sets 联合计算，过滤 completed=false 的组、无效次数/重量、以及孤儿组（无对应 workouts 的 sets）；日期口径统一走 utils/workoutData.dateKey（优先 dateStr → date → createdAt）。页面禁止自己写 dateStr 推导训练日，避免日历、首页打卡、统计对不上。
2. 云函数写入安全：三道闸门——写入白名单（显式构造允许字段，禁止 Object.assign 透传客户端 _id/createdAt 等未知字段，防污染与越权）；更新/删除检查 stats.updated/stats.removed，为 0 返回 NOT_FOUND（防"假成功"）；并发写用 runTransaction 重读最新值再写（agent 会话追加消息防并发覆盖，最多重试 3 次）。
3. 日期真实性：云函数 dateStr 从仅格式校验升级为真实日期校验，拦截 2026-99-99、非闰年 2/29 等，并拒绝未来日期；createdAt 用服务端时间。
4. 日历 N+1 查询：新增 getDayDetail 云函数一次返回某天全部训练 + 组明细（sessionId in 批量查），消除逐条查 sets 的 N+1。
5. 训练 Agent 降级：agent 云函数调 LLM 给出训练/饮食建议；模型不可用时保留本地规则建议作为降级，不阻塞主流程。

## 量化成果
- 11 个 CloudBase 集合，权限统一"仅创建者可读写"。
- 11 个云函数（agent/saveWorkout/ensureUser/savePlan/saveNutritionLog/saveBodyMetric/deletePlan/deleteNutritionLog/deleteUserData/updateUserActive/getDayDetail）。
- 纯逻辑 Node 测试脚本覆盖统计/历史/计划/记录保存（scripts/test_stats.js、test_history.js、test_plan.js、test_record_plan.js、test_record_save.js）。
- 浅色/深色双主题 + 字体可读性系统（app.wxss 设计 token + docs/architecture/ui-design-system.md），以 375×812 为基准、rpx 映射，SVG 图标按主题切 -light 版。

## 复盘与可追问点
- 为什么不用独立后端（Spring Boot + MySQL）：初期只想要轻量记录工具，CloudBase 自带账号体系(_openid)与文档库，免运维；训练数据量小且敏感，云函数 + 文档库足够。代价是复杂关联查询必须上云函数聚合（如 getDayDetail），不能像 SQL 那样 JOIN，需要提前设计聚合口径。
- 为什么不接 GIF/图片动作库：内置文字动作库零 CDN 依赖、零版权风险、加载快；动作演示不是 MVP 核心，先做对"记录"这件事。
- 为什么聚合逻辑锁死在 utils：多人/多页面各自实现口径极易导致统计对不上，集中纯函数 + 单测是最稳的做法，也是这个项目最能体现工程严谨的地方。
- 如果重做：会把 bodyMetrics 这类"每天一条"数据尽早用 upsert（已上线）；并发写尽早统一走事务；规则训练建议做成可配置而非硬编码；再考虑是否引入独立后端承接 AI/图片等更重的能力。
