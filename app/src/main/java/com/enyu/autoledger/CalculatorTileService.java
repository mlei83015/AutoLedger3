package com.enyu.autoledger;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class CalculatorTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel("記帳計算機");
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Runnable open = () -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(MainActivity.ACTION_QUICK_CALCULATOR);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityAndCollapse(intent);
        };
        if (isLocked()) unlockAndRun(open);
        else open.run();
    }
}
