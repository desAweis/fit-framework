# 智能体

## 简介

2024 年 Snowflake 峰会开发者日上，人工智能领域的领军人物吴恩达发表了题为“AI 代理工作流及其推动 AI 进展的潜力的演讲，为我们揭开了
Agentic AI 的神秘面纱，并指出这可能是比下一代基础模型更具潜力的 AI 发展方向。零样本 (Zero-shot) 模式下的 GPT-4 在
HumanEval (代码生成测评数据集) 上的准确率也只有 67.0%，但经过 Agent 加持的 GPT-3.5 准确率能飙升到惊人的 95.1%。也就是说，Agent
能带来显著的效果提升。

目前，有 4 种主要的 Agent 设计模式，分别是：

1）Reflection：让 Agent 审视和修正自己生成的输出；
2）Tool Use：LLM 生成代码、调用 API 等进行实际操作；
3）Planning：让 Agent 分解复杂任务并按计划执行；
4）Multiagent Collaboration：多个 Agent 扮演不同角色合作完成任务。

目前应用比较多的是 2) 和 4)，`FEL` 针对工具调用和多智能体提出了针对性的解决方案，可以轻松实现工具调用的多智能体互通。

## 工具调用


## 示例

在本例子中，我们将创建一个天气助手，并定义两个 `FIT` 函数 `get_current_temperature` 和 `get_rain_probability` 作为助手可以调用的工具。

1. 在项目 pom.xml 加入以下依赖：

``` xml
<dependencies>
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fit-starter</artifactId>
        <version>${fit.version}</version>
    </dependency>
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fit-plugins-starter-web</artifactId>
        <version>${fit.version}</version>
    </dependency>
    <dependency>
        <groupId>org.fitframework.plugin</groupId>
        <artifactId>fit-http-client-okhttp</artifactId>
        <version>${fit.version}</version>
    </dependency>
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-core</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-flow</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-model-openai-plugin</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <!-- 工具自动发现服务 -->
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-tool-discoverer</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <!-- 工具执行服务 -->
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-tool-executor</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <!-- Fit 工具工厂 -->
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-tool-factory-fit</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <!-- 工具工厂存储 -->
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-tool-factory-repository</artifactId>
        <version>${fel.version}</version>
    </dependency>
    <!-- 工具存储 -->
    <dependency>
        <groupId>org.fitframework</groupId>
        <artifactId>fel-tool-repository-simple</artifactId>
        <version>${fel.version}</version>
    </dependency>
</dependencies>

<plugins>
    <!-- 工具编译插件 -->
    <plugin>
        <groupId>org.fitframework.fel</groupId>
        <artifactId>tool-maven-plugin</artifactId>
        <version>${fel.version}</version>
        <executions>
            <execution>
                <id>build-tool</id>
                <goals>
                    <goal>build-tool</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</plugins>
```

2. 在 application.yml 配置文件中加入以下配置：

```yaml
fel:
  openai:
    api-base: '${api-base}'
    api-key: '${your-api-key}'
example:
  model: '${model-name}'
```

3. 定义工具

定义天气相关的工具服务，同时使用 @ToolMethod 定义工具元数据。相关接口如下：

``` java
public interface WeatherService {
    @Genericable("modelengine.example.weather.temperature")
    String getCurrentTemperature(String location, String unit);

    @Genericable("modelengine.example.weather.rain")
    String getRainProbability(String location);
}
```

service 层如下：
```
@Component
public class WeatherServiceImpl implements WeatherService {
    @Override
    @Fitable("default")
    @ToolMethod(namespace = "example", name = "get_current_temperature", description = "获取指定城市的当前温度")
    public String getCurrentTemperature(@Property(description = "城市名称", required = true) String location,
            @Property(description = "使用的温度单位，可选：Celsius，Fahrenheit", defaultValue = "Celsius") String unit) {
        return "26";
    }

    @Override
    @Fitable("default")
    @ToolMethod(namespace = "example", name = "get_rain_probability", description = "获取指定城市下雨的概率")
    public String getRainProbability(@Property(description = "城市名称", required = true) String location) {
        return "0.06";
    }
}
```

4. 使用默认 agent 进行调度：

``` java
@RequestMapping("/ai/example")
public class AgentExampleController {
    private final AiProcessFlow<String, ChatMessage> agentFlow;
    private final ChatOption chatOption;
    private final ToolRepository toolRepository;

    public AgentExampleController(ChatModel chatModel, ToolExecuteService toolExecuteService,
            ToolRepository toolRepository, @Value("${example.model}") String modelName) {
        this.toolRepository = toolRepository;
        this.chatOption = ChatOption.custom().model(modelName).stream(false).build();
        DefaultAgent agent =
                new DefaultAgent(new ChatFlowModel(chatModel, this.chatOption), "example", toolExecuteService);
        this.agentFlow = AiFlows.<String>create()
                .map(query -> Tip.fromArray(query))
                .prompt(Prompts.human("{{0}}"))
                .delegate(agent)
                .close();
    }

    @GetMapping("/chat")
    public ChatMessage chat(@RequestParam("query") String query) {
        List<ToolInfo> toolInfos = asParent(toolRepository.listTool("example"));
        return this.agentFlow.converse()
                .bind(ChatOption.custom(this.chatOption).tools(toolInfos).build())
                .offer(query)
                .await();
    }
}
```

## 验证

- 在浏览器栏输入：`http://localhost:8080/ai/example/chat?query=北京下雨的概率是多少`

```json
{
  "content": "北京下雨的概率是6%。",
  "toolCalls": []
}
```
可以看到，大模型根据工具返回的概率进行回答。
