/*
 * Copyright 2016 MovingBlocks
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
package org.terasology.rendering.opengl;

import org.lwjgl.opengl.GL11;
import org.terasology.math.geom.Vector3f;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DECR;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FILL;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11.GL_INCR;
import static org.lwjgl.opengl.GL11.GL_KEEP;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_LINE;
import static org.lwjgl.opengl.GL11.GL_NOTEQUAL;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_COLOR;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glStencilFunc;
import static org.lwjgl.opengl.GL20.glStencilOpSeparate;
import static org.terasology.rendering.opengl.OpenGLUtils.bindDisplay;
import static org.terasology.rendering.opengl.OpenGLUtils.setRenderBufferMask;

/**
 * The GraphicState class aggregates a number of methods setting the OpenGL state
 * before and after rendering passes.
 *
 * In many circumstances these methods do little more than binding the appropriate
 * Frame Buffer Object (FBO) and texture buffers so the OpenGL implementation and
 * the shaders know where to read from and write to. In some cases more involved
 * OpenGL state changes occur, i.e. in the lighting-related methods.
 *
 * Also, most methods come in pairs named preRenderSetup*() and postRenderCleanup*(),
 * reflecting their use before or after an actual render takes place. Usually the first
 * method changes an unspecified default state while the second method reinstates it.
 *
 * A number of FBO references are kept up to date through the refreshDynamicFBOs() and
 * setSceneShadowMap() methods. At this stage the FrameBuffersManager class is tasked
 * with running these methods whenever its FBOs change.
 */
public class GraphicState {
    // As this class pretty much always deals with OpenGL states, it did occur to me
    // that it might be better called OpenGLState or something along that line. I
    // eventually decided for GraphicState as it resides in the rendering.opengl
    // package anyway and rendering.opengl.OpenGLState felt cumbersome. --emanuele3d
    private FrameBuffersManager buffersManager;
    private Buffers buffers = new Buffers();

    /**
     * Graphic State constructor.
     *
     * This constructor only sets the internal reference to the rendering process. It does not obtain
     * all the references to FBOs nor it initializes the other instance it requires to operate correctly.
     * As such, it relies on the caller to make sure that at the appropriate time (when the buffers are
     * available) refreshDynamicFBOs() and setSceneShadowMap() are called and the associated internal
     * FBO references are initialized.
     *
     * @param buffersManager An instance of the FrameBuffersManager class, used to obtain references to its FBOs.
     */
    public GraphicState(FrameBuffersManager buffersManager) {
        // a reference to the frameBuffersManager is not strictly necessary, as it is used only
        // in refreshDynamicFBOs() and it could be passed as argument to it. We do it this
        // way however to maintain similarity with the way the PostProcessor works.
        this.buffersManager = buffersManager;
    }

    /**
     * This method disposes of a GraphicState instance by simply nulling a number of internal references.
     *
     * It is probably not strictly necessary as the Garbage Collection mechanism should be able to dispose
     * instances of this class without much trouble once they are out of scope. But it is probably good
     * form to include and use a dispose() method, to make it explicit when an instance will no longer be
     * useful.
     */
    public void dispose() {
        buffersManager = null;
        buffers = null;
    }

    /**
     * Used to initialize and eventually refresh the internal references to FBOs primarily
     * held by the FrameBuffersManager instance.
     *
     * Instances of the GraphicState class cannot operate unless this method has been called
     * at least once, the FBOs retrieved through it not null. It then needs to be called again
     * every time the FrameBuffersManager instance changes its FBOs. This occurs whenever
     * the display resolution changes or when a screenshot is taken with a resolution that
     * is different from that of the display.
     */
    public void refreshDynamicFBOs() {
        buffers.sceneOpaque               = buffersManager.getFBO("sceneOpaque");
        buffers.sceneReflectiveRefractive = buffersManager.getFBO("sceneReflectiveRefractive");
        buffers.sceneReflected            = buffersManager.getFBO("sceneReflected");
    }

    public void setSceneOpaqueFBO(FBO newSceneOpaque) {
        buffers.sceneOpaque = newSceneOpaque;
    }

    /**
     * Used to initialize and update the internal reference to the Shadow Map FBO.
     *
     * Gets called every time the FrameBuffersManager instance changes the ShadowMap FBO.
     * This will occur whenever the ShadowMap resolution is changed, i.e. via the rendering
     * settings.
     *
     * @param newShadowMap the FBO containing the new shadow map buffer
     */
    public void setSceneShadowMap(FBO newShadowMap) {
        buffers.sceneShadowMap = newShadowMap;
    }

    /**
     * Initial clearing of a couple of important Frame Buffers. Then binds back the Display.
     */
    // It's unclear why these buffers need to be cleared while all the others don't...
    public void initialClearing() {
        buffers.sceneOpaque.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        buffers.sceneReflectiveRefractive.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        bindDisplay();
    }

    /**
     * Readies the state to render the Opaque scene.
     *
     * The opaque scene includes a number of successive passes including the backdrop (i.e. the skysphere),
     * the landscape (chunks/blocks), additional objects associated with the landscape (i.e. other players/fauna),
     * overlays (i.e. the cursor-cube) and the geometry associated with the first person view, i.e. the objects
     * held in hand.
     */
    public void preRenderSetupSceneOpaque() {
        buffers.sceneOpaque.bind();
        setRenderBufferMask(buffers.sceneOpaque, true, true, true);
    }

    /**
     * Resets the state after the rendering of the Opaque scene.
     *
     * See preRenderSetupSceneOpaque() for more details about
     * the Opaque scene rendering.
     */
    public void postRenderCleanupSceneOpaque() {
        setRenderBufferMask(buffers.sceneOpaque, true, true, true); // TODO: probably redundant - verify
        bindDisplay();
    }

    /**
     * Sets the state to render in wireframe.
     *
     * @param wireframeIsEnabledInRenderingDebugConfig If True enables wireframe rendering. False, does nothing.
     */
    public void enableWireframeIf(boolean wireframeIsEnabledInRenderingDebugConfig) {
        if (wireframeIsEnabledInRenderingDebugConfig) {
            GL11.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        }
    }

    /**
     * Disables wireframe rendering. Used together with enableWireFrameIf().
     *
     * @param wireframeIsEnabledInRenderingDebugConfig If True disables wireframe rendering. False, does nothing.
     */
    public void disableWireframeIf(boolean wireframeIsEnabledInRenderingDebugConfig) {
        if (wireframeIsEnabledInRenderingDebugConfig) {
            GL11.glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }
    }


    /**
     * Sets the state to render the First Person View.
     *
     * This generally comprises the objects held in hand, i.e. a pick, an axe, a torch and so on.
     */
    public void preRenderSetupFirstPerson() {
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDepthFunc(GL11.GL_ALWAYS);
    }

    /**
     * Resets the state after the render of the First Person View.
     *
     * See preRenderSetupFirstPerson() for some more information.
     */
    public void postRenderClenaupFirstPerson() {
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glPopMatrix();
    }

    // TODO: figure how lighting works and what this does
    public void preRenderSetupLightGeometryStencil() {
        buffers.sceneOpaque.bind();
        setRenderBufferMask(buffers.sceneOpaque, false, false, false);
        glDepthMask(false);

        glClear(GL_STENCIL_BUFFER_BIT);

        glCullFace(GL_FRONT);
        glDisable(GL_CULL_FACE);

        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_ALWAYS, 0, 0);

        glStencilOpSeparate(GL_BACK, GL_KEEP, GL_INCR, GL_KEEP);
        glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_DECR, GL_KEEP);
    }

    // TODO: figure how lighting works and what this does
    public void postRenderCleanupLightGeometryStencil() {
        setRenderBufferMask(buffers.sceneOpaque, true, true, true);
        bindDisplay();
    }

    // TODO: figure how lighting works and what this does
    public void preRenderSetupLightGeometry() {
        buffers.sceneOpaque.bind();

        // Only write to the light buffer
        setRenderBufferMask(buffers.sceneOpaque, false, false, true);

        glStencilFunc(GL_NOTEQUAL, 0, 0xFF);

        glDepthMask(true);
        glDisable(GL_DEPTH_TEST);

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_COLOR);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
    }

    // TODO: figure how lighting works and what this does
    public void postRenderCleanupLightGeometry() {
        glDisable(GL_STENCIL_TEST);
        glCullFace(GL_BACK);

        bindDisplay();
    }

    // TODO: figure how lighting works and what this does
    public void preRenderSetupDirectionalLights() {
        buffers.sceneOpaque.bind();
    }

    // TODO: figure how lighting works and what this does
    public void postRenderCleanupDirectionalLights() {
        glDisable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnable(GL_DEPTH_TEST);

        setRenderBufferMask(buffers.sceneOpaque, true, true, true);
        bindDisplay();
    }

    /**
     * Sets the state for the rendering of the reflective/refractive features of the scene.
     *
     * At this stage this is the surface of water bodies, reflecting the sky and (if enabled)
     * the surrounding landscape, and refracting the underwater scenery.
     *
     * If the isHeadUnderWater argument is set to True, the state is further modified to
     * accommodate the rendering of the water surface from an underwater point of view.
     *
     * @param isHeadUnderWater Set to True if the point of view is underwater, to render the water surface correctly.
     */
    public void preRenderSetupSceneReflectiveRefractive(boolean isHeadUnderWater) {
        buffers.sceneReflectiveRefractive.bind();

        // Make sure the water surface is rendered if the player is underwater.
        if (isHeadUnderWater) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    /**
     * Resets the state after the rendering of the reflective/refractive features of the scene.
     *
     * See preRenderSetupSceneReflectiveRefractive() for additional information.
     *
     * @param isHeadUnderWater Set to True if the point of view is underwater, for some additional resetting.
     */
    public void postRenderCleanupSceneReflectiveRefractive(boolean isHeadUnderWater) {
        if (isHeadUnderWater) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        bindDisplay();
    }

    /**
     * Sets the state for the rendering of objects or portions of objects having some degree of transparency.
     *
     * Generally speaking objects drawn with this state will have their color blended with the background
     * color, depending on their opacity. I.e. a 25% opaque foreground object will provide 25% of its
     * color while the background will provide the remaining 75%. The sum of the two RGBA vectors gets
     * written onto the output buffer.
     *
     * Important note: this method disables writing to the Depth Buffer. This is why filters relying on
     * depth information (i.e. DoF) have problems with transparent objects: the depth of their pixels is
     * found to be that of the background. This is an unresolved (unresolv-able?) issue that would only
     * be reversed, not eliminated, by re-enabling writing to the Depth Buffer.
     */
    public void preRenderSetupSimpleBlendMaterials() {
        buffers.sceneOpaque.bind();
        setRenderBufferMask(buffers.sceneOpaque, true, true, true);

        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // (*)
        GL11.glDepthMask(false);

        // (*) In this context SRC is Foreground. This effectively says:
        // Resulting RGB = ForegroundRGB * ForegroundAlpha + BackgroundRGB * (1 - ForegroundAlpha)
        // Which might still look complicated, but it's actually the most typical alpha-driven composite.
        // A neat tool to play with this settings can be found here: http://www.andersriggelsen.dk/glblendfunc.php
    }

    /**
     * Resets the state after the rendering of semi-opaque/semi-transparent objects.
     *
     * See preRenderSetupSimpleBlendMaterials() for additional information.
     */
    public void postRenderCleanupSimpleBlendMaterials() {
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(true);

        setRenderBufferMask(buffers.sceneOpaque, true, true, true); // TODO: review - this might be redundant.
        bindDisplay();
    }

    /**
     * Sets the state prior to the rendering of a chunk.
     *
     * In practice this just positions the chunk appropriately, relative to the camera.
     *
     * @param chunkPositionRelativeToCamera Effectively: chunkCoordinates * chunkDimensions - cameraCoordinate
     */
    public void preRenderSetupChunk(Vector3f chunkPositionRelativeToCamera) {
        GL11.glPushMatrix();
        GL11.glTranslatef(chunkPositionRelativeToCamera.x, chunkPositionRelativeToCamera.y, chunkPositionRelativeToCamera.z);
    }

    /**
     * Resets the state after the rendering of a chunk.
     *
     * See preRenderSetupChunk() for additional information.
     */
    public void postRenderCleanupChunk() {
        GL11.glPopMatrix();
    }



    private class Buffers {
        public FBO sceneOpaque;
        public FBO sceneReflectiveRefractive;
        public FBO sceneReflected;
        public FBO sceneShadowMap;
    }
}
