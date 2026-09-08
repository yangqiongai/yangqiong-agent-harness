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
package com.yangqiongai.agent.harness.core.message;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Agent图像块
 * @author yangqiong
 */
@JsonDeserialize(builder = AgentImageBlock.Builder.class)
public final class AgentImageBlock implements AgentContentBlock {

    /**
     * 图像URL
     */
    private final String url;

    /**
     * Base64编码数据
     */
    private final String base64Data;

    /**
     * 媒体类型
     */
    private final String mediaType;

    private AgentImageBlock(String url, String base64Data, String mediaType) {
        this.url = url;
        this.base64Data = base64Data;
        this.mediaType = mediaType;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取图像URL
     * @return
     */
    public String getUrl() {
        return url;
    }

    /**
     * 获取Base64编码数据
     * @return
     */
    public String getBase64Data() {
        return base64Data;
    }

    /**
     * 获取媒体类型
     * @return
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * 是否为URL模式
     * @return
     */
    @JsonIgnore
    public boolean isUrlMode() {
        return url != null && !url.isBlank();
    }

    /**
     * 是否为Base64模式
     * @return
     */
    @JsonIgnore
    public boolean isBase64Mode() {
        return base64Data != null && !base64Data.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentImageBlock that = (AgentImageBlock) o;
        return Objects.equals(url, that.url)
                && Objects.equals(base64Data, that.base64Data)
                && Objects.equals(mediaType, that.mediaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, base64Data, mediaType);
    }

    @Override
    public String toString() {
        if (isUrlMode()) {
            return "AgentImageBlock{url='" + url + "'}";
        }
        return "AgentImageBlock{base64DataLength=" + (base64Data != null ? base64Data.length() : 0)
                + ", mediaType='" + mediaType + "'}";
    }

    /**
     * 图像块构建器
     * @author yangqiong
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        /**
         * 图像URL
         */
        private String url;

        /**
         * Base64编码数据
         */
        private String base64Data;

        /**
         * 媒体类型
         */
        private String mediaType;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public AgentImageBlock build() {
            return new AgentImageBlock(url, base64Data, mediaType);
        }
    }
}
