package com.divinedoll.launcher.input;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonSyntaxException;
import com.kdt.pickafile.FileListView;
import com.kdt.pickafile.FileSelectedListener;

import com.divinedoll.launcher.game.GameView;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.input.buttons.ControlButton;
import com.divinedoll.launcher.input.buttons.ControlDrawer;
import com.divinedoll.launcher.input.buttons.ControlInterface;
import com.divinedoll.launcher.input.buttons.ControlJoystick;
import com.divinedoll.launcher.input.buttons.ControlSubButton;
import com.divinedoll.launcher.input.handleview.ActionRow;
import com.divinedoll.launcher.input.handleview.ControlHandleView;
import com.divinedoll.launcher.input.handleview.EditControlSideDialog;
import com.divinedoll.launcher.game.platform.Platform;
import com.divinedoll.launcher.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControlLayout extends FrameLayout {
	protected CustomControls mLayout;
	/* Accessible when inside the game by ControlInterface implementations, cached for perf. */
	private GameView mGameSurface = null;

	/* Cache to buttons for performance purposes */
	private List<ControlInterface> mButtons;
	private boolean mModifiable = false;
	private boolean mIsModified;
	private boolean mControlVisible = false;

	private float mButtonsOpacity = 1.0f;

	private EditControlSideDialog mControlDialog = null;
	private ControlHandleView mHandleView;
	private ControlButtonMenuListener mMenuListener;
	public ActionRow mActionRow = null;
	public String mLayoutFileName;

	public ControlLayout(Context ctx) {
		super(ctx);
	}

	public ControlLayout(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
	}


	public void loadLayout(String jsonPath) throws IOException, JsonSyntaxException {
		mUndoStack.clear();
		mRedoStack.clear();
		Point size = new Point(getWidth(), getHeight());
        try {
            CustomControls layout = LayoutConverter.loadAndConvertIfNecessary(size, jsonPath);
            loadLayout(layout);
            updateLoadedFileName(jsonPath);
        }catch (IOException | JsonSyntaxException e) {
            // Load an empty layout on exception to avoid breakage when adding buttons in the editor
            CustomControls customControls = new CustomControls();
            customControls.mLayoutBitmaps = LayoutBitmaps.createEmpty();
            loadLayout(customControls);
            throw e;
        }
	}

	private static final int MAX_HISTORY = 30;
	private final java.util.ArrayDeque<String> mUndoStack = new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<String> mRedoStack = new java.util.ArrayDeque<>();

	private String snapshot() {
		return mLayout == null ? null : Tools.GLOBAL_GSON.toJson(mLayout);
	}

	/** Remembers the layout as it is now, so the next change can be undone. */
	public void rememberState() {
		String state = snapshot();
		if(state == null || state.equals(mUndoStack.peek())) return;
		if(mUndoStack.size() >= MAX_HISTORY) mUndoStack.removeLast();
		mUndoStack.push(state);
		mRedoStack.clear();
	}

	private void restore(String json) {
		CustomControls layout = Tools.GLOBAL_GSON.fromJson(json, CustomControls.class);
		layout.mLayoutBitmaps = mLayout.mLayoutBitmaps;
		removeEditWindow();
		loadLayout(layout);
		setModified(true);
	}

	/** Goes back to the state before the last change. The current one can be recovered with redo. */
	public void undo() {
		if(mUndoStack.isEmpty() || mLayout == null) return;
		String current = snapshot();
		String previous = mUndoStack.pop();
		if(current != null && current.equals(previous) && !mUndoStack.isEmpty()) previous = mUndoStack.pop();
		mRedoStack.push(current);
		restore(previous);
	}

	public void redo() {
		if(mRedoStack.isEmpty() || mLayout == null) return;
		String current = snapshot();
		mUndoStack.push(current);
		restore(mRedoStack.pop());
	}

	public void loadLayout(CustomControls controlLayout) {
		sInstance = this;
		this.mButtonsOpacity = (float) LauncherPreferences.PREF_BUTTON_TRANSPARENCY / 100;
		boolean sanitizedModified = false;
		if(controlLayout != null) {
			sanitizedModified = LayoutSanitizer.sanitizeLayout(controlLayout);
		}
		if(mActionRow == null){
			mActionRow = new ActionRow(getContext());
			addView(mActionRow);
		}

		removeAllButtons();
		if(mLayout != null) {
			mLayout.mControlDataList = null;
			mLayout = null;
		}

		System.gc();
		mapTable.clear();

		// Cleanup buttons only when input layout is null
		if (controlLayout == null) return;

		mLayout = controlLayout;
		

		// Joystick(s) first, to workaround the touch dispatch
		for(ControlJoystickData joystick : mLayout.mJoystickDataList){
			addJoystickView(joystick);
		}

		//CONTROL BUTTON
		for (ControlData button : controlLayout.mControlDataList) {
			addControlView(button);
		}

		//CONTROL DRAWER
		for(ControlDrawerData drawerData : controlLayout.mDrawerDataList){
			ControlDrawer drawer = addDrawerView(drawerData);
			if(mModifiable) drawer.areButtonsVisible = true;
		}

		mLayout.scaledAt = LauncherPreferences.PREF_BUTTONSIZE;

		setModified(sanitizedModified);
		mButtons = null;
		getButtonChildren(); // Force refresh
	} // loadLayout

	//CONTROL BUTTON
	public void addControlButton(ControlData controlButton) {
		rememberState();
		mLayout.mControlDataList.add(controlButton);
		addControlView(controlButton);
	}

	private void addControlView(ControlData controlButton) {
		final ControlButton view = new ControlButton(this, controlButton);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity * mButtonsOpacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);

		setModified(true);
	}

	// CONTROL DRAWER
	/** Adds a drawer with a few ready buttons that use the pixel button as their background. */
	public void addOptionPack() {
		addOptionPack(0);
	}

	/** Adds a drawer that opens downward. Type 0 is the menu, 1 the camera tools and 2 the item tools. */
	public void addOptionPack(int type) {
		int[][] keys = {{292, 258, 66}, {290, 291, 294, 300}, {81, 70, 69}};
		int[][] labels = {
				{R.string.fork_ctl_pack_debug, R.string.fork_ctl_pack_players, R.string.fork_ctl_pack_emotes},
				{R.string.fork_ctl_pack_hud, R.string.fork_ctl_pack_screenshot, R.string.fork_ctl_pack_camera, R.string.fork_ctl_pack_fullscreen},
				{R.string.fork_ctl_pack_drop, R.string.fork_ctl_pack_swap, R.string.fork_ctl_pack_inventory}};
		type = Math.max(0, Math.min(type, keys.length - 1));
		ControlDrawerData pack = new ControlDrawerData();
		pack.properties.name = "";
		pack.properties.bitmapTag = menuBitmapKey();
		pack.properties.dynamicX = (0.56f + 0.06f * type) + " * ${screen_width}";
		pack.properties.dynamicY = "0.0";
		pack.properties.setWidth(Tools.dpToPx(36f));
		pack.properties.setHeight(Tools.dpToPx(36f));
		pack.orientation = ControlDrawerData.Orientation.DOWN;
		pack.subWidth = 190f;
		pack.subHeight = 40f;
		for(int i = 0; i < keys[type].length; i++) {
			ControlData button = new ControlData(getContext().getString(labels[type][i]), new int[]{keys[type][i]}, 0f, 0f, 190f, 40f);
			button.bitmapTag = packBitmapKey(false, 190, 40);
			button.textColor = 0xFF000000;
			button.textLeft = true;
			pack.buttonProperties.add(button);
		}
		addDrawer(pack);
	}

	/** Uses the image of the pause button (three bars) of the current layout, or the default one. */
	private String menuBitmapKey() {
		for(ControlData data : mLayout.mControlDataList) {
			if(data.keycodes == null || data.keycodes.length == 0 || (data.keycodes[0] != 256 && data.keycodes[0] != 111) || !Tools.isValidString(data.bitmapTag)) continue;
			android.graphics.Bitmap existing = getBitmaps().getBitmap(data.bitmapTag);
			if(existing != null) return getBitmaps().putBitmap(existing.copy(android.graphics.Bitmap.Config.ARGB_8888, false), null);
		}
		return getBitmaps().putBitmap(android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.pack_menu_button), null);
	}

	private String packBitmapKey(boolean blue, int widthDp, int heightDp) {
		android.graphics.Bitmap texture = com.divinedoll.launcher.utils.PackTexture.make(getContext(), (int) Tools.dpToPx(widthDp), (int) Tools.dpToPx(heightDp), blue);
		return getBitmaps().putBitmap(texture, null);
	}

	public void addDrawer(ControlDrawerData drawerData){
		rememberState();
		mLayout.mDrawerDataList.add(drawerData);
		addDrawerView();
	}

	private void addDrawerView(){
		addDrawerView(null);
	}

	private ControlDrawer addDrawerView(ControlDrawerData drawerData){

		final ControlDrawer view = new ControlDrawer(this,drawerData == null ? mLayout.mDrawerDataList.get(mLayout.mDrawerDataList.size()-1) : drawerData);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity * mButtonsOpacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);
		//CONTROL SUB BUTTON
		for (ControlData subButton : view.getDrawerData().buttonProperties) {
			addSubView(view, subButton);
		}

		setModified(true);
		return view;
	}

	//CONTROL SUB-BUTTON
	public void addSubButton(ControlDrawer drawer, ControlData controlButton){
		//Yep there isn't much here
		drawer.getDrawerData().buttonProperties.add(controlButton);
		addSubView(drawer, drawer.getDrawerData().buttonProperties.get(drawer.getDrawerData().buttonProperties.size()-1 ));
	}

	private void addSubView(ControlDrawer drawer, ControlData controlButton){
		final ControlSubButton view = new ControlSubButton(this, controlButton, drawer);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity * mButtonsOpacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}else{
			view.setVisible(true);
		}

		addView(view);
		drawer.addButton(view);


		setModified(true);
	}

	// JOYSTICK BUTTON
	public void addJoystickButton(ControlJoystickData data){
		rememberState();
		mLayout.mJoystickDataList.add(data);
		addJoystickView(data);
	}

	private void addJoystickView(ControlJoystickData data){
		ControlJoystick view = new ControlJoystick(this, data);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity * mButtonsOpacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);

	}


	private void removeAllButtons() {
		for(ControlInterface button : getButtonChildren()){
			removeView(button.getControlView());
		}

		System.gc();
		//i wanna be sure that all the removed Views will be removed after a reload
		//because if frames will slowly go down after many control changes it will be warm and bad
	}

	public void saveLayout(String path) throws Exception {
		mLayout.save(path);
		setModified(false);
	}

	public void toggleControlVisible(){
		mControlVisible = !mControlVisible;
		setControlVisible(mControlVisible);
	}

	public float getLayoutScale(){
		return mLayout.scaledAt;
	}

	public CustomControls getLayout(){
		return mLayout;
	}

	public static final String PREF_MIRROR = "mirror_controls";

	public static boolean isMirrored() {
		return LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_MIRROR, false);
	}

	/** Left-handed mode: every control is flipped horizontally. */
	public void toggleMirror() {
		if(mModifiable) return;
		LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_MIRROR, !isMirrored()).apply();
		for(ControlInterface button : getButtonChildren()) button.getControlView().requestLayout();
		requestLayout();
	}

	private static ControlLayout sInstance;
	private static long sLastHud;

	/** Called by every F1 key release (touch buttons and hardware keyboards). Toggling twice within 250 ms is ignored. */
	public static void onGlobalF1() {
		final ControlLayout layout = sInstance;
		if(layout == null) return;
		long now = System.currentTimeMillis();
		if(now - sLastHud < 250) return;
		sLastHud = now;
		layout.post(layout::toggleHud);
	}

	public void applyJoystickDeadzone() {
		int zone = com.divinedoll.launcher.utils.JoystickSettings.deadzone();
		for(ControlInterface button : getButtonChildren()) {
			if(button instanceof com.divinedoll.launcher.input.buttons.ControlJoystick) {
				((com.divinedoll.launcher.input.buttons.ControlJoystick) button).applyDeadzone(zone);
			}
		}
	}

	private boolean mHudHidden = false;

	public boolean isHudHidden() {
		return mHudHidden;
	}

	public static boolean sendsF1(ControlData d) {
		if(d == null || d.keycodes == null) return false;
		for(int k : d.keycodes) if(k == 290) return true;
		return false;
	}

	/** While the F1 mode is on, only the buttons that send F1 (and the drawers holding one) stay on screen. */
	public boolean isButtonAllowed(ControlInterface button) {
		if(!mHudHidden) return true;
		if(sendsF1(button.getProperties())) return true;
		if(button instanceof com.divinedoll.launcher.input.buttons.ControlDrawer) {
			for(com.divinedoll.launcher.input.buttons.ControlSubButton sub : ((com.divinedoll.launcher.input.buttons.ControlDrawer) button).buttons) {
				if(sendsF1(sub.getProperties())) return true;
			}
		}
		return false;
	}

	/** F1: hides the touch controls, pressing it again brings them back. */
	public void toggleHud() {
		if(mModifiable) return;
		mHudHidden = !mHudHidden;
		for(ControlInterface button : getButtonChildren()) {
			// Open drawers are closed so only the F1 button can show up again
			if(button instanceof com.divinedoll.launcher.input.buttons.ControlDrawer) {
				((com.divinedoll.launcher.input.buttons.ControlDrawer) button).closeButtons();
			}
		}
		setControlVisible(mControlVisible);
		if(!mHudHidden && mControlVisible) {
			for(ControlInterface button : getButtonChildren()) {
				if(button instanceof com.divinedoll.launcher.input.buttons.ControlSubButton) continue;
				if(!button.getProperties().isHideable) button.getControlView().setVisibility(View.VISIBLE);
			}
		}
	}

	public void setControlVisible(boolean isVisible) {
		if (mModifiable) return; // Not using on custom controls activity

		mControlVisible = isVisible;
		for(ControlInterface button : getButtonChildren()){
            // Avoid going through the JNI each time.
            button.setVisible(isButtonAllowed(button) && ((button.getProperties().displayInGame && Platform.isGrabbing()) || (button.getProperties().displayInMenu && !Platform.isGrabbing())) && isVisible);
		}
	}

	public void setModifiable(boolean isModifiable) {
		if(!isModifiable && mModifiable){
			removeEditWindow();
		}
		mModifiable = isModifiable;
		updateButtonOpacity();
	}

	public boolean getModifiable(){
		return mModifiable;
	}

	public void setModified(boolean isModified) {
		mIsModified = isModified;
	}

	public List<ControlInterface> getButtonChildren(){
		if(mModifiable || mButtons == null){
			mButtons = new ArrayList<>();
			for(int i=0; i<getChildCount(); ++i){
				View v = getChildAt(i);
				if(v instanceof ControlInterface)
					mButtons.add(((ControlInterface) v));
			}
		}

		return mButtons;
	}

	public void refreshControlButtonPositions(){
		requestLayout();
	}

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if(child instanceof ControlInterface && mControlDialog != null){
			mControlDialog.disappearColor();
            mControlDialog.disappear(false);
        }
    }

    /**
	 * Load the layout if needed, and pass down the burden of filling values
	 * to the button at hand.
	 */
	public void editControlButton(ControlInterface button){
		rememberState();
		if(mControlDialog == null){
			// When the panel is null, it needs to inflate first.
			// So inflate it, then process it on the next frame
			mControlDialog = new EditControlSideDialog(getContext(), this);
			post(() -> editControlButton(button));
			return;
		}

		mControlDialog.internalChanges = true;
		mControlDialog.setCurrentlyEditedButton(button);

		mControlDialog.appear(button.getControlView().getX() + button.getControlView().getWidth()/2f < getWidth()/2f);
		button.loadEditValues(mControlDialog);

		mControlDialog.internalChanges = false;

		mControlDialog.disappearColor();

		if(mHandleView == null){
			mHandleView = new ControlHandleView(getContext());
			addView(mHandleView);
		}
		mHandleView.setControlButton(button);

		//mHandleView.show();
	}

	/** Swap the panel if the button position requires it */
	public void adaptPanelPosition(){
		if(mControlDialog != null) mControlDialog.adaptPanelPosition();
	}


	final HashMap<View, ControlInterface> mapTable = new HashMap<>();

	private static boolean eventInViewBounds(MotionEvent event, View view) {
		float x = event.getX();
		float y = event.getY();
		return x > view.getLeft() && x < view.getRight() && y > view.getTop() && y < view.getBottom();
	}

	//While this is called onTouch, this should only be called from a ControlButton.
	public void onTouch(View v, MotionEvent ev) {
		int action = ev.getActionMasked();
		ControlInterface lastControlButton = mapTable.get(v);

		// Map location to screen coordinates
		ev.offsetLocation(v.getX(), v.getY());


		//Check if the action is cancelling, reset the lastControl button associated to the view
		if (action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_POINTER_UP) {
			if (lastControlButton != null) lastControlButton.handleReleased();
			mapTable.put(v, null);
			return;
		}

		if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_DOWN) return;

		//Optimization pass to avoid looking at all children again
		if (lastControlButton != null) {
			if (eventInViewBounds(ev, lastControlButton.getControlView())) {
				return;
			}
		}

		//Release last keys
		if (lastControlButton != null) lastControlButton.handleReleased();
		mapTable.remove(v);

		// Update the state of all swipeable buttons
		for (ControlInterface button : getButtonChildren()) {
			if (!button.getProperties().isSwipeable) continue;
			if (eventInViewBounds(ev, button.getControlView())) {
				//Press the new key
				if (!button.equals(lastControlButton)) {
					button.handlePressed();
					mapTable.put(v, button);
					return;
				}

			}
		}
	}

    @RequiresApi(30)
    private boolean isKeyboardShown() {
        WindowInsets windowInsets = getRootWindowInsets();
        Insets imeInsets = windowInsets.getInsets(WindowInsets.Type.ime());
        return imeInsets.bottom != 0 || imeInsets.left != 0 || imeInsets.top != 0 || imeInsets.right != 0;
    }

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mModifiable && event.getActionMasked() != MotionEvent.ACTION_UP || mControlDialog == null)
			return true;
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

        boolean isKeyboardHidden;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean keyboardShown = isKeyboardShown();
            isKeyboardHidden = !keyboardShown;
            if(keyboardShown) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }else {
            // When the input window cannot be hidden (meaning it's already hidden), it returns false
            // Docs don't seem to suggest that this is the case anymore. But it is on a10 and i
            // don't want to mess with the way insets are done on a10
            isKeyboardHidden = !imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        if(isKeyboardHidden){
			if(mControlDialog.disappearLayer()){
				mActionRow.setFollowedButton(null);
				mHandleView.hide();
			}
		}
		return true;
	}

	public void removeEditWindow() {
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

		// When the input window cannot be hidden, it returns false
		imm.hideSoftInputFromWindow(getWindowToken(), 0);
		if(mControlDialog != null) {
			mControlDialog.disappearColor();
			mControlDialog.disappear(true);
		}

		if(mActionRow != null) mActionRow.setFollowedButton(null);
		if(mHandleView != null) mHandleView.hide();
	}

	public void save(String path){
		try {
			mLayout.save(path);
		} catch (IOException e) {Log.e("ControlLayout", "Failed to save the layout at:" + path);}
	}


	public boolean hasMenuButton() {
		for(ControlInterface controlInterface : getButtonChildren()){
			for (int keycode : controlInterface.getProperties().keycodes) {
				if (keycode == ControlData.SPECIALBTN_MENU) return true;
			}
		}
		return false;
	}

	public void setMenuListener(ControlButtonMenuListener menuListener) {
		this.mMenuListener = menuListener;
	}

	public void notifyAppMenu() {
		if(mMenuListener != null) mMenuListener.onClickedMenu();
	}

	/** Cached getter for perf purposes */
	public GameView getGameSurface(){
		if(mGameSurface == null){
			mGameSurface = findViewById(R.id.main_game_render_view);
		}
		return mGameSurface;
	}

	public void askToExit(EditorExitable editorExitable) {
		if(mIsModified) {
			openSaveDialog(editorExitable);
		}else{
			openExitDialog(editorExitable);
		}
	}

	public void updateLoadedFileName(String path) {
		path = path.replace(Tools.CTRLMAP_PATH, ".");
		path = path.substring(0, path.length() - 5);
		mLayoutFileName = path;
	}

	public String saveToDirectory(String name) throws Exception{
		String jsonPath = Tools.CTRLMAP_PATH + "/" + name + ".json";
		saveLayout(jsonPath);
		return jsonPath;
	}

	class OnClickExitListener implements View.OnClickListener {
		private final AlertDialog mDialog;
		private final EditText mEditText;
		private final EditorExitable mListener;

		public OnClickExitListener(AlertDialog mDialog, EditText mEditText, EditorExitable mListener) {
			this.mDialog = mDialog;
			this.mEditText = mEditText;
			this.mListener = mListener;
		}

		@Override
		public void onClick(View v) {
			Context context = v.getContext();
			if (mEditText.getText().toString().isEmpty()) {
				mEditText.setError(context.getString(R.string.global_error_field_empty));
				return;
			}
			try {
				String jsonPath = saveToDirectory(mEditText.getText().toString());
				Toast.makeText(context, context.getString(R.string.global_save) + ": " + jsonPath, Toast.LENGTH_SHORT).show();
				mDialog.dismiss();
				if(mListener != null) mListener.exitEditor();
			} catch (Throwable th) {
				Tools.showError(context, th, mListener != null);
			}
		}
	}

	public void openSaveDialog(EditorExitable editorExitable) {
		final Context context = getContext();
		final EditText edit = new EditText(context);
		edit.setSingleLine();
		edit.setText(mLayoutFileName);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.global_save);
		builder.setView(edit);
		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton(android.R.string.cancel, null);
		if(editorExitable != null) builder.setNeutralButton(R.string.global_save_and_exit, null);
		final AlertDialog dialog = builder.create();
		dialog.setOnShowListener(dialogInterface -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE)
					.setOnClickListener(new OnClickExitListener(dialog, edit, null));
			if(editorExitable != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
					.setOnClickListener(new OnClickExitListener(dialog, edit, editorExitable));
		});
		dialog.show();
	}

	public void openLoadDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.global_load);
		builder.setPositiveButton(android.R.string.cancel, null);

		final AlertDialog dialog = builder.create();
		FileListView flv = new FileListView(dialog, "json");
		if(Build.VERSION.SDK_INT < 29)flv.listFileAt(new File(Tools.CTRLMAP_PATH));
		else flv.lockPathAt(new File(Tools.CTRLMAP_PATH));
		flv.setFileSelectedListener(new FileSelectedListener(){

			@Override
			public void onFileSelected(File file, String path) {
				try {
					loadLayout(path);
				}catch (IOException e) {
					Tools.showError(getContext(), e);
				}
				dialog.dismiss();
			}
		});
		dialog.setView(flv);
		dialog.show();
	}

	public void openSetDefaultDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.customctrl_selectdefault);
		builder.setPositiveButton(android.R.string.cancel, null);

		final AlertDialog dialog = builder.create();
		FileListView flv = new FileListView(dialog, "json");
		flv.lockPathAt(new File(Tools.CTRLMAP_PATH));
		flv.setFileSelectedListener(new FileSelectedListener(){

			@Override
			public void onFileSelected(File file, String path) {
				try {
					LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", path).apply();
					LauncherPreferences.PREF_DEFAULTCTRL_PATH = path;loadLayout(path);
				}catch (IOException|JsonSyntaxException e) {
					Tools.showError(getContext(), e);
				}
				dialog.dismiss();
			}
		});
		dialog.setView(flv);
		dialog.show();
	}

	public void openExitDialog(EditorExitable exitListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.customctrl_editor_exit_title);
		builder.setMessage(R.string.customctrl_editor_exit_msg);
		builder.setPositiveButton(R.string.global_yes, (d,w)->exitListener.exitEditor());
		builder.setNegativeButton(R.string.global_no, (d,w)->{});
		builder.show();
	}

	// Copied from https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/FrameLayout.java
	// (and edited to avoid laying out control buttons)
	@SuppressWarnings("RtlHardcoded") // Handled explicitly via getAbsoluteGravity()
	private void layoutNonButtonChildren(int left, int top, int right, int bottom) {
		final int count = getChildCount();
		final int parentLeft = getPaddingLeft();
		final int parentRight = right - left - getPaddingRight();
		final int parentTop = getPaddingTop();
		final int parentBottom = bottom - top - getPaddingBottom();
		final int layoutDirection = getLayoutDirection();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if(child instanceof ControlInterface || child.getVisibility() == GONE) continue;
			final LayoutParams lp = (LayoutParams) child.getLayoutParams();
			final int width = child.getMeasuredWidth();
			final int height = child.getMeasuredHeight();
			int childLeft, childTop;
			int gravity = lp.gravity;
			if (gravity == -1) {
				gravity = Gravity.START | Gravity.TOP;
			}
			final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
			switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
				case Gravity.CENTER_HORIZONTAL:
					childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
							lp.leftMargin - lp.rightMargin;
					break;
				case Gravity.RIGHT:
					childLeft = parentRight - width - lp.rightMargin;
					break;
				case Gravity.LEFT:
				default:
					childLeft = parentLeft + lp.leftMargin;
			}
			switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
				case Gravity.TOP:
				default:
					childTop = parentTop + lp.topMargin;
					break;
				case Gravity.CENTER_VERTICAL:
					childTop = parentTop + (parentBottom - parentTop - height) / 2 +
							lp.topMargin - lp.bottomMargin;
					break;
				case Gravity.BOTTOM:
					childTop = parentBottom - height - lp.bottomMargin;
					break;
			}
			child.layout(childLeft, childTop, childLeft + width, childTop + height);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		layoutNonButtonChildren(left, top, right, bottom);
		int w = right - left;
		int h = bottom - top;

		for(ControlInterface controlInterface : getButtonChildren()) {
			ControlData properties = controlInterface.getProperties();
			View interfaceView = controlInterface.getControlView();

			int width = (int) properties.getWidth();
			int height = (int) properties.getHeight();

			interfaceView.measure(
					MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
			);

			if(!changed && !interfaceView.isLayoutRequested()) {
				interfaceView.layout(
						interfaceView.getLeft(), interfaceView.getTop(),
						interfaceView.getRight(), interfaceView.getBottom()
				);
			} else {
				int l = (int) (properties.insertDynamicPos(properties.dynamicX, w, h) + left);
				int t = (int) (properties.insertDynamicPos(properties.dynamicY, w, h) + top);
				if(!mModifiable && isMirrored()) l = right - (l - left) - width;

				int r = l + width;
				int b = t + height;
				interfaceView.layout(l, t, r, b);
			}
		}
	}

	public boolean areControlVisible(){
		return mControlVisible;
	}

	public LayoutBitmaps getBitmaps() {
		return mLayout.mLayoutBitmaps;
	}

	public void updateButtonOpacity() {
		mButtonsOpacity = Math.clamp((float) LauncherPreferences.PREF_BUTTON_TRANSPARENCY / 100, 0, 1);
		for(ControlInterface button : getButtonChildren()) {
			// In edit mode, all controls have to be shown
			if(mModifiable) button.setVisible(true);
			button.getControlView().setAlpha(mModifiable ? button.getProperties().opacity : mButtonsOpacity * button.getProperties().opacity);
		}
	}
}
