package me.zhengziheng.agent.service.agent;

import java.util.Map;

/**
 * Agent 工具接口（可插拔，面试讲点）：
 * 工具说明是「提示工程」的一部分——LLM 依据 description 决定何时调用、parameters 决定传什么参数，
 * 因此两段文案必须写清"何时用、传什么"。新增工具只需实现本接口并注册为 Spring Bean，
 * AgentToolRegistry 会自动收集（构造器注入 List&lt;AgentTool&gt;）。
 */
public interface AgentTool {

    /** 工具名（LLM 在 ACTION 行输出的名字，必须与这里一致） */
    String name();

    /** 工具说明（LLM 可见：什么时候该用它） */
    String description();

    /** 参数说明（LLM 可见：参数名、类型、是否必填、取值建议） */
    String parameters();

    /** 执行工具：返回结果文本与命中片段；参数非法时抛异常（异常信息会回填给 LLM 继续尝试） */
    AgentToolResult execute(Map<String, Object> args);
}
