package me.zhengziheng.agent.service.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表：Spring 自动收集所有 AgentTool 实现（构造器注入 List），按名字查找。
 * 新增工具 = 新增一个 @Component 实现，零注册代码（可插拔设计）。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> byName;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.byName = tools.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity()));
    }

    /** 按名字取工具；不存在返回 null（上层回填错误信息让 LLM 修正） */
    public AgentTool get(String name) {
        return name == null ? null : byName.get(name.trim());
    }

    /** 全部工具清单（LLM 可见的工具说明，供系统提示词使用） */
    public List<AgentTool> all() {
        return List.copyOf(byName.values());
    }
}
