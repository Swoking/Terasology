/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.logic.chat;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.input.ButtonState;
import org.terasology.input.binds.general.ChatButton;
import org.terasology.logic.chat.commands.TellCommand;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.console.*;
import org.terasology.logic.console.internal.referenced.Command;
import org.terasology.logic.console.internal.referenced.CommandParameter;
import org.terasology.logic.console.ui.MiniChatOverlay;
import org.terasology.network.ClientComponent;
import org.terasology.registry.In;
import org.terasology.rendering.FontColor;
import org.terasology.rendering.nui.NUIManager;

/**
 * @author Immortius
 */
@RegisterSystem
public class ChatSystem extends BaseComponentSystem {
    private static final Logger logger = LoggerFactory.getLogger(ChatSystem.class);

    private static final AssetUri CHAT_UI = new AssetUri(AssetType.UI_ELEMENT, "engine:chat");
    private static final AssetUri CONSOLE_UI = new AssetUri(AssetType.UI_ELEMENT, "engine:console");
    private static final AssetUri MINICHAT_UI = new AssetUri(AssetType.UI_ELEMENT, "engine:minichatOverlay");
    
    @In
    private Console console;
    
    @In
    private EntityManager entityManager;

    @In
    private NUIManager nuiManager;

    private MiniChatOverlay overlay;

    @Override
    public void initialise() {
        overlay = nuiManager.addOverlay(MINICHAT_UI, MiniChatOverlay.class);
    }

    @ReceiveEvent(components = ClientComponent.class)
    public void onToggleChat(ChatButton event, EntityRef entity) {
        if (event.getState() == ButtonState.DOWN) {
            nuiManager.pushScreen(CHAT_UI);
            overlay.setVisible(false);
            event.consume();
        }
    }

    @ReceiveEvent(components = ClientComponent.class)
    public void onMessage(MessageEvent event, EntityRef entity) {
        ClientComponent client = entity.getComponent(ClientComponent.class);
        if (client.local) {
            Message message = event.getFormattedMessage();
            if (message.getType() == CoreMessageType.CHAT || message.getType() == CoreMessageType.NOTIFICATION) {

                // show overlay only if chat and console are hidden
                if (!nuiManager.isOpen(CHAT_UI) && !nuiManager.isOpen(CONSOLE_UI)) {
                    overlay.setVisible(true);
                }
            }
        }
    }
}
