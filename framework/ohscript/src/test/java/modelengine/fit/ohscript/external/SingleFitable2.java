/*---------------------------------------------------------------------------------------------
 *  Copyright (c) 2024 Huawei Technologies Co., Ltd. All rights reserved.
 *  This file is a part of the ModelEngine Project.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package modelengine.fit.ohscript.external;

import modelengine.fitframework.annotation.Fitable;
import modelengine.fitframework.annotation.FitableSuite;

import java.util.List;
import java.util.Map;

/**
 * 表示 {@link SingleGenericable} 的实现 2。
 *
 * @author 季聿阶
 * @since 2023-11-01
 */
@FitableSuite
public class SingleFitable2 implements SingleGenericable {
    @Override
    @Fitable(id = "f2")
    public List<Map<String, Object>> handleTask(List<Map<String, Object>> param) {
        param.get(0).put("k2", "v2");
        return param;
    }
}
