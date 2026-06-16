package com.desertkun.brainout;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.desertkun.brainout.controllers.GameController;
import com.desertkun.brainout.events.GameControllerEvent;

/**
 * Twin-stick touch controller.
 *
 * Two floating sticks driven by raw multi-touch pointers: a touch in the left
 * half of the screen drives the move stick, a touch in the right half drives the
 * aim stick (which also auto-fires past a threshold). A small cluster of action
 * buttons (reload / weapon / use / crouch) lives on a {@link Stage}; only those
 * buttons consume Stage input — the sticks are tracked here directly so move and
 * aim+fire work simultaneously.
 *
 * Active only in the {@code action} / {@code actionWithNoMouseLocking} modes.
 */
public class AndroidGameController extends GameController
{
    private static final int NONE = -1;

    private Stage ui;
    private Image moveBase, moveKnob, aimBase, aimKnob;
    private Table buttons;

    private int movePointer = NONE;
    private int aimPointer = NONE;

    private final Vector2 moveOrigin = new Vector2();
    private final Vector2 moveCur = new Vector2();
    private final Vector2 aimOrigin = new Vector2();
    private final Vector2 aimCur = new Vector2();

    private final Vector2 moveVec = new Vector2();   // current move direction sent
    private final Vector2 aimMouse = new Vector2();  // scratch for absolute aim
    private final Vector2 stageTmp = new Vector2();

    private boolean firing;
    private boolean running;
    private boolean crouching;

    private int lastWidth = -1, lastHeight = -1;

    @Override
    public void init()
    {
        // The HUD is built lazily (ensureUi) the first time the combat mode
        // activates: the skin drawables it needs (touchpad-*, button styles) are
        // only loaded with the mainmenu package, well after this early init().
    }

    private void ensureUi()
    {
        if (ui != null)
            return;

        ui = new Stage(new ScreenViewport());

        moveBase = skinImage("touchpad-background");
        moveKnob = skinImage("touchpad-move");
        aimBase = skinImage("touchpad-background");
        aimKnob = skinImage("touchpad-aim");

        for (Image img : new Image[]{ moveBase, moveKnob, aimBase, aimKnob })
        {
            img.setVisible(false);
            ui.addActor(img);
        }

        buttons = buildButtons();
        ui.addActor(buttons);
    }

    private Image skinImage(String drawable)
    {
        Image img = new Image(BrainOutClient.Skin.getDrawable(drawable));
        img.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return img;
    }

    private Table buildButtons()
    {
        Table table = new Table();
        table.setFillParent(true);
        table.align(Align.bottomRight);

        TextButton weapon = textButton("WEAPON");
        TextButton reload = textButton("RELOAD");
        TextButton crouch = textButton("CROUCH");
        TextButton use = textButton("USE");

        weapon.addListener(tap(() -> sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.switchWeapon))));
        reload.addListener(tap(() -> sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.reload))));
        use.addListener(tap(() -> sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.activate))));
        crouch.addListener(tap(() ->
        {
            crouching = !crouching;
            sendEvent(GameControllerEvent.obtain(crouching
                ? GameControllerEvent.Action.beginSit
                : GameControllerEvent.Action.endSit));
        }));

        float pad = minDim() * AndroidConstants.Touch.BUTTON_PAD;

        table.add(weapon).pad(pad);
        table.add(reload).pad(pad);
        table.row();
        table.add(crouch).pad(pad);
        table.add(use).pad(pad);
        table.pad(minDim() * AndroidConstants.Touch.BUTTON_PAD);

        return table;
    }

    private TextButton textButton(String text)
    {
        return new TextButton(text, BrainOutClient.Skin, "button-small");
    }

    private ClickListener tap(Runnable action)
    {
        return new ClickListener()
        {
            @Override
            public void clicked(InputEvent event, float x, float y)
            {
                action.run();
            }
        };
    }

    // ------------------------------------------------------------------ input

    private boolean hudActive()
    {
        return controllerMode == ControllerMode.action
            || controllerMode == ControllerMode.actionWithNoMouseLocking;
    }

    private float minDim()
    {
        return Math.min(BrainOutClient.getWidth(), BrainOutClient.getHeight());
    }

    private float radius()
    {
        return minDim() * AndroidConstants.Touch.STICK_RADIUS;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button)
    {
        if (!hudActive())
            return false;

        // Buttons get first refusal.
        if (hitsButton(screenX, screenY))
        {
            ui.touchDown(screenX, screenY, pointer, button);
            return true;
        }

        boolean left = screenX < BrainOutClient.getWidth() / 2f;

        if (left && movePointer == NONE)
        {
            movePointer = pointer;
            moveOrigin.set(screenX, screenY);
            moveCur.set(screenX, screenY);
            showStick(moveBase, moveKnob, moveOrigin, moveOrigin);
        }
        else if (!left && aimPointer == NONE)
        {
            aimPointer = pointer;
            aimOrigin.set(screenX, screenY);
            aimCur.set(screenX, screenY);
            showStick(aimBase, aimKnob, aimOrigin, aimOrigin);
        }

        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer)
    {
        if (pointer == movePointer)
        {
            moveCur.set(screenX, screenY);
            return true;
        }
        if (pointer == aimPointer)
        {
            aimCur.set(screenX, screenY);
            return true;
        }
        if (hudActive() && ui != null)
        {
            ui.touchDragged(screenX, screenY, pointer);
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button)
    {
        if (pointer == movePointer)
        {
            movePointer = NONE;
            moveVec.setZero();
            stopMoving();
            hideStick(moveBase, moveKnob);
            return true;
        }
        if (pointer == aimPointer)
        {
            aimPointer = NONE;
            stopFiring();
            hideStick(aimBase, aimKnob);
            return true;
        }
        if (hudActive() && ui != null)
        {
            ui.touchUp(screenX, screenY, pointer, button);
        }
        return false;
    }

    private boolean hitsButton(float screenX, float screenY)
    {
        stageTmp.set(screenX, screenY);
        ui.screenToStageCoordinates(stageTmp);
        Actor hit = ui.hit(stageTmp.x, stageTmp.y, true);
        return hit != null && (hit == buttons || hit.isDescendantOf(buttons));
    }

    // ----------------------------------------------------------------- update

    @Override
    public void update(float dt)
    {
        if (ui == null)
            return;

        syncViewport();
        ui.act(dt);

        if (!hudActive())
            return;

        updateMove();
        updateAim();
        updateKnobs();
    }

    private void updateMove()
    {
        if (movePointer == NONE)
            return;

        float range = radius() * AndroidConstants.Touch.KNOB_RANGE;
        float dx = clampRange(moveCur.x - moveOrigin.x, range);
        float dy = clampRange(moveCur.y - moveOrigin.y, range);

        float moveTh = AndroidConstants.Touch.MOVE_THRESHOLD * range;

        float x = dx > moveTh ? 1 : (dx < -moveTh ? -1 : 0);
        // screen Y grows downward: finger up (dy < 0) means move up (+1).
        float y = dy < -moveTh ? 1 : (dy > moveTh ? -1 : 0);

        if (x != moveVec.x || y != moveVec.y)
        {
            moveVec.set(x, y);
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.move, moveVec));
        }

        boolean wantRun = len(dx, dy) > AndroidConstants.Touch.RUN_THRESHOLD * range;
        if (wantRun && !running)
        {
            running = true;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.beginRun));
        }
        else if (!wantRun && running)
        {
            running = false;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.endRun));
        }
    }

    private void updateAim()
    {
        if (aimPointer == NONE)
            return;

        float range = radius() * AndroidConstants.Touch.KNOB_RANGE;
        float dx = aimCur.x - aimOrigin.x;
        float dy = aimCur.y - aimOrigin.y;
        float frac = len(dx, dy) / range;

        if (frac > AndroidConstants.Touch.AIM_DEAD_ZONE)
        {
            aimMouse.set(dx, dy).nor().scl(AndroidConstants.Touch.AIM_RADIUS_PX);
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.absoluteAim, aimMouse));
        }

        boolean wantFire = frac > AndroidConstants.Touch.FIRE_THRESHOLD;
        if (wantFire && !firing)
        {
            firing = true;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.beginLaunch, 0));
        }
        else if (!wantFire && firing)
        {
            firing = false;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.endLaunch, 0));
        }
    }

    private void stopMoving()
    {
        sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.move, moveVec.setZero()));
        if (running)
        {
            running = false;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.endRun));
        }
    }

    private void stopFiring()
    {
        if (firing)
        {
            firing = false;
            sendEvent(GameControllerEvent.obtain(GameControllerEvent.Action.endLaunch, 0));
        }
    }

    // ------------------------------------------------------------- knob render

    private void showStick(Image base, Image knob, Vector2 originScreen, Vector2 knobScreen)
    {
        placeCentered(base, originScreen, radius() * 2f);
        placeCentered(knob, knobScreen, radius());
        base.setVisible(true);
        knob.setVisible(true);
    }

    private void hideStick(Image base, Image knob)
    {
        base.setVisible(false);
        knob.setVisible(false);
    }

    private void updateKnobs()
    {
        float range = radius() * AndroidConstants.Touch.KNOB_RANGE;

        if (movePointer != NONE)
        {
            float dx = clampRange(moveCur.x - moveOrigin.x, range);
            float dy = clampRange(moveCur.y - moveOrigin.y, range);
            stageTmp.set(moveOrigin.x + dx, moveOrigin.y + dy);
            placeCentered(moveKnob, stageTmp, radius());
        }
        if (aimPointer != NONE)
        {
            float dx = clampRange(aimCur.x - aimOrigin.x, range);
            float dy = clampRange(aimCur.y - aimOrigin.y, range);
            stageTmp.set(aimOrigin.x + dx, aimOrigin.y + dy);
            placeCentered(aimKnob, stageTmp, radius());
        }
    }

    private void placeCentered(Image img, Vector2 screenPoint, float size)
    {
        stageTmp.set(screenPoint.x, screenPoint.y);
        ui.screenToStageCoordinates(stageTmp);
        img.setSize(size, size);
        img.setPosition(stageTmp.x - size / 2f, stageTmp.y - size / 2f);
    }

    // ----------------------------------------------------------------- helpers

    private static float clampRange(float v, float range)
    {
        if (v > range) return range;
        if (v < -range) return -range;
        return v;
    }

    private static float len(float x, float y)
    {
        return (float) Math.sqrt(x * x + y * y);
    }

    private void syncViewport()
    {
        int w = BrainOutClient.getWidth();
        int h = BrainOutClient.getHeight();
        if (w != lastWidth || h != lastHeight)
        {
            ui.getViewport().update(w, h, true);
            lastWidth = w;
            lastHeight = h;
        }
    }

    @Override
    public void setControllerMode(ControllerMode controllerMode)
    {
        super.setControllerMode(controllerMode);
        applyMode();
    }

    private void applyMode()
    {
        boolean active = hudActive();

        if (active)
            ensureUi();

        if (ui == null)
            return;

        buttons.setVisible(active);

        if (!active)
        {
            // Release anything held so we don't get stuck moving/firing.
            if (movePointer != NONE || aimPointer != NONE)
            {
                stopMoving();
                stopFiring();
            }
            movePointer = NONE;
            aimPointer = NONE;
            hideStick(moveBase, moveKnob);
            hideStick(aimBase, aimKnob);
        }
    }

    @Override
    public void render()
    {
        if (ui != null)
            ui.draw();
    }

    @Override
    public boolean keyDown(int keycode)
    {
        return (ui != null && ui.keyDown(keycode)) || super.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode)
    {
        return (ui != null && ui.keyUp(keycode)) || super.keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character)
    {
        return (ui != null && ui.keyTyped(character)) || super.keyTyped(character);
    }

    @Override
    public boolean scrolled(float amountX, float amountY)
    {
        return (ui != null && ui.scrolled(amountX, amountY)) || super.scrolled(amountX, amountY);
    }
}
