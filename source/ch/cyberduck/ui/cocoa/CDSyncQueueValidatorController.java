package ch.cyberduck.ui.cocoa;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.Status;

import com.apple.cocoa.application.*;
import com.apple.cocoa.foundation.NSAttributedString;
import com.apple.cocoa.foundation.NSSelector;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @version $Id$
 */
public class CDSyncQueueValidatorController extends CDValidatorController {
    private static Logger log = Logger.getLogger(CDSyncQueueValidatorController.class);

    public CDSyncQueueValidatorController(final CDWindowController parent) {
        super(parent);
        synchronized(NSApplication.sharedApplication()) {
            if(!NSApplication.loadNibNamed("Sync", this)) {
                log.fatal("Couldn't load Sync.nib");
            }
            this.setEnabled(false);
        }
    }

    /**
     * The list of files for possibly synchronisationw
     * using either the mirror, download or upload option
     */
    private List promptList = new ArrayList();

    public void awakeFromNib() {
        NSSelector setResizableMaskSelector
                = new NSSelector("setResizingMask", new Class[]{int.class});
        {
            NSTableColumn c = new NSTableColumn();
            c.setIdentifier(TYPE_COLUMN);
            c.headerCell().setStringValue("");
            c.setMinWidth(20f);
            c.setWidth(20f);
            c.setMaxWidth(20f);
            if(setResizableMaskSelector.implementedByClass(NSTableColumn.class)) {
                c.setResizingMask(NSTableColumn.AutoresizingMask);
            }
            else {
                c.setResizable(true);
            }
            c.setEditable(false);
            c.setDataCell(new NSImageCell());
            c.dataCell().setAlignment(NSText.CenterTextAlignment);
            this.fileTableView.addTableColumn(c);
        }
        {
            NSTableColumn c = new NSTableColumn();
            c.setIdentifier(NEW_COLUMN);
            c.headerCell().setStringValue("");
            c.setMinWidth(20f);
            c.setWidth(20f);
            c.setMaxWidth(20f);
            if(setResizableMaskSelector.implementedByClass(NSTableColumn.class)) {
                c.setResizingMask(NSTableColumn.AutoresizingMask);
            }
            else {
                c.setResizable(true);
            }
            c.setEditable(false);
            c.setDataCell(new NSImageCell());
            c.dataCell().setAlignment(NSText.CenterTextAlignment);
            this.fileTableView.addTableColumn(c);
        }
        this.fileTableView.sizeToFit();
        super.awakeFromNib();
    }

    /**
     * The lock used to determine if a sheet should be displayed
     */
    private static final Object lock = new Object();

    public void prompt(Path p) {
        // Check if the timestamps are different or either the remote or local file doesn't exist
        if(p.compare() != 0) {
            synchronized(lock) {
                if(!this.hasPrompt()) {
                    this.beginSheet(false);
                    this.hasPrompt = true;
                }
            }
            this.promptList.add(p);
            this.updateSelection(p);
            this.fireDataChanged();
        }
    }

    protected void setEnabled(boolean enabled) {
        this.syncButton.setEnabled(enabled);
    }

    /**
     *
     */
    private void updateSelection() {
        this.workList.clear();
        for(Iterator i = this.promptList.iterator(); i.hasNext();) {
            this.updateSelection((Path) i.next());
        }
        this.fireDataChanged();
    }

    /**
     *
     * @param p
     */
    private void updateSelection(final Path p) {
        if(!(downloadRadioCell.state() == NSCell.OnState) && !p.getLocal().exists()) {
            return;
        }
        if(!(uploadRadioCell.state() == NSCell.OnState) && !p.getRemote().exists()) {
            return;
        }
        this.workList.add(p);
    }

    // ----------------------------------------------------------
    // Outlets
    // ----------------------------------------------------------

    protected NSButton downloadRadioCell;

    public void setDownloadRadioCell(NSButton downloadRadioCell) {
        this.downloadRadioCell = downloadRadioCell;
        this.downloadRadioCell.setState(NSCell.OnState);
        this.downloadRadioCell.setTarget(this);
        this.downloadRadioCell.setAction(new NSSelector("downloadCellClicked", new Class[]{Object.class}));
    }

    public void downloadCellClicked(final Object sender) {
        this.updateSelection();
    }
    
    protected NSButton uploadRadioCell;

    public void setUploadRadioCell(NSButton uploadRadioCell) {
        this.uploadRadioCell = uploadRadioCell;
        this.uploadRadioCell.setState(NSCell.OnState);
        this.uploadRadioCell.setTarget(this);
        this.uploadRadioCell.setAction(new NSSelector("uploadCellClicked", new Class[]{Object.class}));
    }

    public void uploadCellClicked(final Object sender) {
        this.updateSelection();
    }

    protected NSButton syncButton;

    public void setSyncButton(NSButton syncButton) {
        this.syncButton = syncButton;
    }

    public void callback(final int returncode) {
        if(returncode == DEFAULT_OPTION) { //sync
            for(Iterator i = this.workList.iterator(); i.hasNext();) {
                final Path p = (Path) i.next();
                if(p.isSkipped()) {
                    this.workList.remove(p);
                }
            }
        }
        else {
            super.callback(returncode);
        }
    }

    protected static final String TYPE_COLUMN = "TYPE";
    protected static final String NEW_COLUMN = "NEW";

    private static final NSImage ARROW_UP_ICON = NSImage.imageNamed("arrowUp16.tiff");
    private static final NSImage ARROW_DOWN_ICON = NSImage.imageNamed("arrowDown16.tiff");
    private static final NSImage PLUS_ICON = NSImage.imageNamed("plus.tiff");

    /**
     * @see NSTableView.DataSource
     */
    public Object tableViewObjectValueForLocation(NSTableView view, NSTableColumn column, int row) {
        if(row < this.numberOfRowsInTableView(view)) {
            String identifier = (String) column.identifier();
            Path p = (Path) this.workList.get(row);
            if(p != null) {
                if (identifier.equals(SIZE_COLUMN)) {
                    return new NSAttributedString(Status.getSizeAsString(
                            p.compare() > 0 ? p.getRemote().attributes.getSize() : p.getLocal().attributes.getSize()),
                            CDTableCell.PARAGRAPH_DICTIONARY_RIGHHT_ALIGNEMENT);
                }
                if(identifier.equals(TYPE_COLUMN)) {
                    return p.compare() > 0 ? ARROW_DOWN_ICON : ARROW_UP_ICON;
                }
                if(identifier.equals(WARNING_COLUMN)) {
                    if(p.getRemote().exists())
                        if(p.getRemote().attributes.getSize() == 0)
                            return ALERT_ICON;
                    if(p.getLocal().exists()) {
                        if(p.getLocal().attributes.getSize() == 0)
                            return ALERT_ICON;
                    }
                    return null;
                }
                if(identifier.equals(NEW_COLUMN)) {
                    if(!(p.getRemote().exists() && p.getLocal().exists())) {
                        return PLUS_ICON;
                    }
                    return null;
                }
                return super.tableViewObjectValueForLocation(view, column, row);
            }
        }
        return null;
    }
}