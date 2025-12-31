package com.mine.autosleep;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SleepTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();

        boolean active = SleepController.isSleepActive(this);

        Intent work = new Intent(this, AutoSleepService.class);
        work.putExtra(Constants.ID, active ? Constants.ID_DISABLE : Constants.ID_ENABLE);
        AutoSleepService.enqueue(this, work);

        // Update immediately (UI)
        updateTileState(!active);
    }

    private void updateTileState() {
        updateTileState(SleepController.isSleepActive(this));
    }

    private void updateTileState(boolean active) {
        Tile t = getQsTile();
        if (t == null) return;

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
               t.updateTile();
    }
}
