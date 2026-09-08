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
package com.yangqiongai.agent.harness.local.tool;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文本解析器测试
 * @author yangqiong
 */
class LocalTextDocumentParserTest {

    /**
     * 解析器实例
     */
    private final LocalTextDocumentParser parser = new LocalTextDocumentParser();

    /**
     * 验证css代码文件可正常解析
     */
    @Test
    void parsesCssContent() {
        String css = "body { background: #fff; color: #333; }";
        assertThat(parser.parse(css.getBytes(StandardCharsets.UTF_8), "style.css"))
                .isEqualTo(css);
    }

    /**
     * 验证js与ts代码文件可正常解析
     */
    @Test
    void parsesJsAndTsContent() {
        String js = "const a = 1;";
        String ts = "const b: number = 2;";
        assertThat(parser.parse(js.getBytes(StandardCharsets.UTF_8), "app.js")).isEqualTo(js);
        assertThat(parser.parse(ts.getBytes(StandardCharsets.UTF_8), "app.ts")).isEqualTo(ts);
    }

    /**
     * 验证二进制扩展名仍被拒绝
     */
    @Test
    void rejectsUnsupportedBinaryExtension() {
        byte[] bytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        assertThatThrownBy(() -> parser.parse(bytes, "logo.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件格式");
    }
}
