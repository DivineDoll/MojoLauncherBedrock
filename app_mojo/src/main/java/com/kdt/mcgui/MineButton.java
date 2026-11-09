package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.util.*;

import com.divinedoll.launcher.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		com.divinedoll.launcher.utils.McStyle.button(this, "primary".equals(getTag()));
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
	}


    @Override
    public boolean performClick() {
        com.divinedoll.launcher.utils.ClickSound.play();
        return super.performClick();
    }
}
