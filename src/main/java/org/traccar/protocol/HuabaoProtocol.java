/*
 * Copyright 2015 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import org.traccar.BaseProtocol;
import org.traccar.PipelineBuilder;
import org.traccar.TrackerServer;
import org.traccar.config.Config;
import org.traccar.model.Command;

import jakarta.inject.Inject;

public class HuabaoProtocol extends BaseProtocol {

        /**
     * 构造函数，初始化华宝协议处理器
     *
     * @param config 配置对象，用于初始化服务器和协议处理相关参数
     */
    @Inject
    public HuabaoProtocol(Config config) {
        // 设置支持的数据命令类型
        setSupportedDataCommands(
                Command.TYPE_CUSTOM,
                Command.TYPE_REBOOT_DEVICE,
                Command.TYPE_POSITION_PERIODIC,
                Command.TYPE_ALARM_ARM,
                Command.TYPE_ALARM_DISARM,
                Command.TYPE_ENGINE_STOP,
                Command.TYPE_ENGINE_RESUME);

        // 添加服务器实例并配置协议编解码器
        addServer(new TrackerServer(config, getName(), false) {
            @Override
            protected void addProtocolHandlers(PipelineBuilder pipeline, Config config) {
                pipeline.addLast(new HuabaoFrameEncoder());
                pipeline.addLast(new HuabaoFrameDecoder());
                pipeline.addLast(new HuabaoProtocolEncoder(HuabaoProtocol.this));
                pipeline.addLast(new HuabaoProtocolDecoder(HuabaoProtocol.this));
            }
        });
    }


}
