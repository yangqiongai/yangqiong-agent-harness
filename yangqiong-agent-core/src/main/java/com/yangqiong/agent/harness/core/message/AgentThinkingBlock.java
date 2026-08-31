/*
 * Copyright 2026 yangqiongtech.com
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
package com.yangqiong.agent.harness.core.message;

import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Agent思考块
 * @author yangqiong
 */
@JsonDeserialize(builder = AgentThinkingBlock.Builder.class)
public final class AgentThinkingBlock implements AgentContentBlock {

    /**
     * 思考内容
     */
    private final String thinking;

    private AgentThinkingBlock(String thinking) {
        this.thinking = thinking;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取思考内容
     * @return
     */
    public String getThinking() {
        return thinking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentThinkingBlock that = (AgentThinkingBlock) o;
        return Objects.equals(thinking, that.thinking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thinking);
    }

    @Override
    public String toString() {
        return "AgentThinkingBlock{thinking='" + thinking + "'}";
    }

    /**
     * 思考块构建器
     * @author yangqiong
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        /**
         * 思考内容
         */
        private String thinking;

        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public AgentThinkingBlock build() {
            return new AgentThinkingBlock(thinking);
        }
    }
}
