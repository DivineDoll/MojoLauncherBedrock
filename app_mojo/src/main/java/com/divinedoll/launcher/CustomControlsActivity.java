package com.divinedoll.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.gson.JsonSyntaxException;

import com.divinedoll.launcher.input.ControlData;
import com.divinedoll.launcher.input.ControlDrawerData;
import com.divinedoll.launcher.input.ControlJoystickData;
import com.divinedoll.launcher.input.ControlLayout;
import com.divinedoll.launcher.input.EditorExitable;
import com.divinedoll.launcher.prefs.LauncherPreferences;
import com.divinedoll.launcher.utils.CropperUtils;

import java.io.IOException;


public class CustomControlsActivity extends BaseActivity implements EditorExitable, CropperUtils.CropperReceiver {
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerNavigationView;
	private ControlLayout mControlLayout;
	private CropperUtils.CropperReceiver mCropperReceiver;
	private ActivityResultLauncher<?> mCropperLauncher;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mCropperLauncher = CropperUtils.registerCropper(this, this);

		setContentView(R.layout.activity_custom_controls);

		mControlLayout = findViewById(R.id.customctrl_controllayout);
		mDrawerLayout = findViewById(R.id.customctrl_drawerlayout);
		mDrawerNavigationView = findViewById(R.id.customctrl_navigation_view);
		View mPullDrawerButton = findViewById(R.id.drawer_button);

		mPullDrawerButton.setOnClickListener(v -> mDrawerLayout.openDrawer(mDrawerNavigationView));
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

		mDrawerNavigationView.setBackgroundColor(com.divinedoll.launcher.utils.ThemeColors.sidebar());
		mDrawerNavigationView.setAdapter(com.divinedoll.launcher.utils.McStyle.menuAdapter(this, getResources().getStringArray(R.array.menu_customcontrol_customactivity)));
		mDrawerNavigationView.setOnItemClickListener((parent, view, position, id) -> {
			switch(position) {
				case 0: mControlLayout.addControlButton(new ControlData("New")); break;
				case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
				case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
				case 3: mControlLayout.openLoadDialog(); break;
				case 4: mControlLayout.openSaveDialog(this); break;
				case 5: mControlLayout.openSetDefaultDialog(); break;
				case 7: mControlLayout.undo(); break;
				case 8: mControlLayout.redo(); break;
				case 9:
					new androidx.appcompat.app.AlertDialog.Builder(this)
							.setTitle(R.string.fork_ctl_pack_menu)
							.setItems(getResources().getStringArray(R.array.fork_ctl_pack_types), (d, which) -> mControlLayout.addOptionPack(which))
							.show();
					break;
				case 6: // Saving the currently shown control
					try {
						Uri contentUri = DocumentsContract.buildDocumentUri(getString(R.string.storageProviderAuthorities), mControlLayout.saveToDirectory(mControlLayout.mLayoutFileName));

						Intent shareIntent = new Intent();
						shareIntent.setAction(Intent.ACTION_SEND);
						shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
						shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						shareIntent.setType("application/json");
						startActivity(shareIntent);

						Intent sendIntent = Intent.createChooser(shareIntent, mControlLayout.mLayoutFileName);
						startActivity(sendIntent);
					}catch (Exception e) {
						Tools.showError(this, e);
					}
					break;
			}
			mDrawerLayout.closeDrawers();
		});
		mControlLayout.setModifiable(true);
	}

	@Override
	public void onAttachedToWindow() {
		mControlLayout.post(()->{
			try {
				mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
			}catch (IOException | JsonSyntaxException e) {
				Tools.showError(this, e);
			}
		});
	}

	public void startCropping(CropperUtils.CropperReceiver cropperReceiver) {
		mCropperReceiver = cropperReceiver;
		CropperUtils.startCropper(mCropperLauncher);
	}

	@Override
	public void onBackPressed() {
		mControlLayout.askToExit(this);
	}

	@Override
	public void exitEditor() {
		super.onBackPressed();
	}

	@Override
	public float getAspectRatio() {
		if(mCropperReceiver != null) return mCropperReceiver.getAspectRatio();
		return 1f;
	}

	@Override
	public int getTargetMaxSide() {
		if(mCropperReceiver != null) return mCropperReceiver.getTargetMaxSide();
		return 128;
	}

	@Override
	public void onCropped(Bitmap contentBitmap) {
		if(mCropperReceiver != null) mCropperReceiver.onCropped(contentBitmap);
	}

	@Override
	public void onFailed(Exception exception) {
		if(mCropperReceiver != null) mCropperReceiver.onFailed(exception);
	}
}
