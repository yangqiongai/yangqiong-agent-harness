/*
 * Copyright 2026 yangqiongai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiongai.agent.harness.subagent.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息中枢
 * <p>
 * 多Agent协作编排组件，支持广播式消息路由与轮询讨论。
 * 参与者Agent注册后可接收广播消息，形成多Agent对话协作。
 * </p>
 * @author yangqiong
 */
public class MsgHub {

    private static final Logger log = LoggerFactory.getLogger(MsgHub.class);

    /**
     * 广播历史最大保留条数，超限淘汰最旧消息，防止内存无界增长
     */
    public static final int MAX_HISTORY = 1000;

    /**
     * 参与者Agent映射（按名称索引）
     */
    private final Map<String, AgentRuntime> participants = new ConcurrentHashMap<>();

    /**
     * 广播消息历史
     */
    private final List<AgentMessage> broadcastHistory = new CopyOnWriteArrayList<>();

    /**
     * 注册参与者Agent
     * @param agent
     */
    public void register(AgentRuntime agent) {
        if (agent != null && agent.getName() != null) {
            participants.put(agent.getName(), agent);
        }
    }

    /**
     * 注销参与者Agent
     * @param name
     */
    public void unregister(String name) {
        participants.remove(name);
    }

    /**
     * 广播消息给所有参与者，收集每个Agent的回复
     * @param message 广播消息文本
     * @param context 运行时上下文
     * @return 各Agent回复的映射（Agent名称→回复内容）
     */
    public Mono<Map<String, String>> broadcast(String message, AgentRuntimeContext context) {
        AgentMessage broadcastMsg = createBroadcastMessage(message);
        // 历史超限淘汰最旧消息
        if (broadcastHistory.size() >= MAX_HISTORY) {
            broadcastHistory.remove(0);
        }
        broadcastHistory.add(broadcastMsg);
        List<Mono<Map.Entry<String, String>>> monos = new ArrayList<>();
        for (Map.Entry<String, AgentRuntime> entry : participants.entrySet()) {
            String name = entry.getKey();
            AgentRuntime agent = entry.getValue();
            monos.add(agent.call(List.of(broadcastMsg), context)
                    .map(resp -> Map.entry(name, resp != null ? resp.getTextContent() : ""))
                    .onErrorResume(e -> {
                        log.warn("[MsgHub] Agent {} 广播回复失败: {}", name, e.getMessage());
                        return Mono.just(Map.entry(name, "回复失败: " + e.getMessage()));
                    }));
        }
        return Flux.fromIterable(monos)
                .flatMap(mono -> mono, participants.size())
                .collectList()
                .map(entries -> {
                    Map<String, String> result = new LinkedHashMap<>();
                    for (Map.Entry<String, String> e : entries) {
                        result.put(e.getKey(), e.getValue());
                    }
                    return result;
                });
    }

    /**
     * 轮询讨论：按注册顺序依次让每个Agent发言，前一个Agent的输出作为后一个的输入
     * @param topic 讨论主题
     * @param context 运行时上下文
     * @param rounds 讨论轮次
     * @return 讨论历史消息列表
     */
    public Mono<List<AgentMessage>> roundRobin(String topic, AgentRuntimeContext context, int rounds) {
        List<AgentRuntime> ordered = new ArrayList<>(participants.values());
        if (ordered.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        List<AgentMessage> discussion = new ArrayList<>();
        discussion.add(createBroadcastMessage("讨论主题: " + topic));
        return roundRobinStep(ordered, discussion, context, rounds, 0, 0);
    }

    /**
     * 轮询单步执行
     * @param ordered 有序Agent列表
     * @param discussion 讨论历史
     * @param context 运行时上下文
     * @param totalRounds 总轮次
     * @param currentRound 当前轮次
     * @param currentIndex 当前Agent索引
     * @return
     */
    private Mono<List<AgentMessage>> roundRobinStep(List<AgentRuntime> ordered,
                                                      List<AgentMessage> discussion,
                                                      AgentRuntimeContext context,
                                                      int totalRounds, int currentRound, int currentIndex) {
        if (currentRound >= totalRounds) {
            return Mono.just(discussion);
        }
        AgentRuntime agent = ordered.get(currentIndex);
        // 组装带发言人标注与防复读、防代演约束的输入文本，引导参与者给出自己的简短观点
        List<AgentMessage> inputSnapshot = List.of(createBroadcastMessage(buildRoundRobinInput(discussion, agent.getName())));
        return agent.call(inputSnapshot, context)
                .flatMap(response -> {
                    // 附加发言人姓名，供后续轮次组装输入时标注
                    discussion.add(withSpeakerName(agent.getName(), response));
                    int nextIndex = currentIndex + 1;
                    int nextRound = currentRound;
                    if (nextIndex >= ordered.size()) {
                        nextIndex = 0;
                        nextRound = currentRound + 1;
                    }
                    if (nextRound >= totalRounds) {
                        return Mono.just(discussion);
                    }
                    return roundRobinStep(ordered, discussion, context, totalRounds, nextRound, nextIndex);
                })
                .onErrorResume(e -> {
                    log.warn("[MsgHub] 轮询讨论 Agent {} 第{}轮失败: {}",
                            agent.getName(), currentRound + 1, e.getMessage());
                    return Mono.just(discussion);
                });
    }

    /**
     * 创建广播消息
     * @param text
     * @return
     */
    private AgentMessage createBroadcastMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 组装轮转讨论输入文本
     * <p>
     * 首条为主题行，附加防复读与防代演约束；后续发言按发言人标注，帮助参与者区分自己与他人的观点。
     * </p>
     * @param discussion 讨论历史（首条为主题行，后续为各参与者发言）
     * @param speaker 当前发言参与者姓名
     * @return
     */
    private String buildRoundRobinInput(List<AgentMessage> discussion, String speaker) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < discussion.size(); i++) {
            AgentMessage message = discussion.get(i);
            String text = message.getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (i == 0) {
                sb.append(text).append("\n");
                sb.append("请勿重复自己已表达过的观点；你只需以自己【").append(speaker).append("】的身份发言，")
                        .append("不要代替其他参与者发言，也不要输出完整的讨论记录或结论——完整记录与结论由主持者汇总。\n");
            } else {
                String messageSpeaker = message.getName();
                if (messageSpeaker != null && !messageSpeaker.isBlank()) {
                    String prefix = "【" + messageSpeaker + "】";
                    // 参与者可能已自行携带发言人前缀，避免重复标注
                    if (text.startsWith(prefix)) {
                        sb.append(text).append("\n");
                    } else {
                        sb.append(prefix).append(text).append("\n");
                    }
                } else {
                    sb.append(text).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 为发言消息附加发言人姓名
     * @param speaker
     * @param response
     * @return
     */
    private AgentMessage withSpeakerName(String speaker, AgentMessage response) {
        return AgentMessage.builder()
                .name(speaker)
                .role(response.getRole())
                .content(response.getContent())
                .chatUsage(response.getChatUsage())
                .latency(response.getLatency())
                .build();
    }

    /**
     * 获取广播消息历史
     * @return
     */
    public List<AgentMessage> getBroadcastHistory() {
        return new ArrayList<>(broadcastHistory);
    }

    /**
     * 获取参与者数量
     * @return
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * 获取参与者名称列表
     * @return
     */
    public List<String> getParticipantNames() {
        return new ArrayList<>(participants.keySet());
    }

    /**
     * 清理广播历史
     */
    public void clearHistory() {
        broadcastHistory.clear();
    }
}
