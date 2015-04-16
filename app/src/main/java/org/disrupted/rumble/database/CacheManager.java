/*
 * Copyright (C) 2014 Disrupted Systems
 *
 * This file is part of Rumble.
 *
 * Rumble is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rumble is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.events.FileReceivedEvent;
import org.disrupted.rumble.network.events.PushStatusReceivedEvent;
import org.disrupted.rumble.network.events.PushStatusSentEvent;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;
import org.disrupted.rumble.userinterface.events.UserCreateGroup;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.events.UserSetHashTagInterest;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;


import de.greenrobot.event.EventBus;

/**
 * The CacheManager takes care ongf updati the database accordingly to the catched event
 *
 * @author Marlinski
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    private static final Object globalQueuelock = new Object();
    private static CacheManager instance;

    private boolean started;

    public static CacheManager getInstance() {
        synchronized (globalQueuelock) {
            if (instance == null)
                instance = new CacheManager();

            return instance;
        }
    }

    public void start() {
        if(!started) {
            Log.d(TAG, "[+] Starting Cache Manager");
            started = true;
            EventBus.getDefault().register(this);
        }
    }

    public void stop() {
        if(started) {
            Log.d(TAG, "[-] Stopping Cache Manager");
            started = false;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }


    /*
     * Managing Network Interaction
     */
    public void onEvent(PushStatusSentEvent event) {
        if(event.status == null)
            return;
        Log.d(TAG, " [.] status sent: "+event.status.toString());
        PushStatus status = new PushStatus(event.status);
        Iterator<String> it = event.recipients.iterator();
        while(it.hasNext())
            status.addForwarder(it.next(), event.protocolID);
        status.addReplication(event.recipients.size());
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(status, null);
    }

    public void onEvent(PushStatusReceivedEvent event) {
        if(event.status == null)
            return;
        Log.d(TAG, " [.] status received: "+event.status.toString());
        Group group = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroup(event.status.getGroupID());
        if(group == null) {
            // we do not accept message for group we never added
            // group can only be added manually by the user
            Log.d(TAG, "[!] unknown group: refusing the message");
            return;
        } else {
            if(!group.getName().equals(event.group_name)) {
                Log.d(TAG, "[!] GroupID: "+group.getGid()+ " CONFLICT: db="+group.getName()+" status="+event.group_name);
                return;
            }
        }

        Contact contact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(event.status.getAuthorID());
        if(contact == null) {
            contact = new Contact(event.author_name, event.status.getAuthorID(), false);
        } else if(!contact.getName().equals(event.author_name)) {
            // we do not accept message who have a conflict with user name
            Log.d(TAG, "[!] AuthorID: "+contact.getUid()+ " CONFLICT: db="+contact.getName()+" status="+event.author_name);
            return;
        }
        DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact, null);

        PushStatus exists = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.status.getUuid());
        if(exists == null) {
            exists = new PushStatus(event.status);
            exists.addDuplicate(1);
            exists.addForwarder(event.sender, event.protocolID);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).insertStatus(exists, null);
        } else {
            exists.addDuplicate(1);
            exists.addForwarder(event.sender, event.protocolID);
            if(event.status.getLike() > 0)
                exists.addLike();
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
        }
    }
    public void onEvent(FileReceivedEvent event) {
        if(event.filename == null)
            return;
        Log.d(TAG, " [.] file received: "+event.filename);
        PushStatus exists = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if((exists != null) && !exists.hasAttachedFile()) {
            exists.setFileName(event.filename);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
            Log.d(TAG, "[+] status updated: " + exists.getUuid());
            return;
        }
        try {
            File toDelete = new File(FileUtil.getWritableAlbumStorageDir(), event.filename);
            if (toDelete.exists() && toDelete.isFile())
                toDelete.delete();
        }catch(IOException ignore){
        }
    }


    /*
     * Managing User Interaction
     */
    public void onEvent(UserSetHashTagInterest event) {
        if(event.hashtag == null)
            return;
        Log.d(TAG, " [.] tag interest "+event.hashtag+": "+event.levelOfInterest);
        Contact contact = Contact.getLocalContact();
        contact.getHashtagInterests().put(event.hashtag, event.levelOfInterest);
        DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact, null);
    }
    public void onEvent(UserReadStatus event) {
        if(event.uuid == null)
            return;
        Log.d(TAG, " [.] status "+event.uuid+" read");
        PushStatus message = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserLikedStatus event) {
        if(event.uuid == null)
            return;
        Log.d(TAG, " [.] status "+event.uuid+" liked");
        PushStatus message = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserSavedStatus event) {
        if(event.uuid == null)
            return;
        Log.d(TAG, " [.] status "+event.uuid+" saved");
        PushStatus message = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserDeleteStatus event) {
        if(event.uuid == null)
            return;
        Log.d(TAG, " [.] status "+event.uuid+" deleted");
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).deleteStatus(event.uuid, null);
    }

    public void onEvent(UserComposeStatus event) {
        if(event.status == null)
            return;
        Log.d(TAG, " [.] user composed status: "+event.status.toString());
        PushStatus status = new PushStatus(event.status);
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).insertStatus(status, null);
    }
    public void onEvent(UserCreateGroup event) {
        if(event.group == null)
            return;
        Log.d(TAG, " [.] user created group: "+event.group.getName());
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group);
    }
    public void onEvent(UserJoinGroup event) {
        if(event.group == null)
            return;
        Log.d(TAG, " [.] user joined group: "+event.group.getName());
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group);
    }
}
