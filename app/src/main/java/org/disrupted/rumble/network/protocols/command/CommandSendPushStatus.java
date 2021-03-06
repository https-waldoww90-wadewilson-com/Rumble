/*
 * Copyright (C) 2014 Lucien Loiseau
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

package org.disrupted.rumble.network.protocols.command;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.PushStatus;

import java.util.Set;

/**
 * @author Lucien Loiseau
 */
public class CommandSendPushStatus extends Command {

    private PushStatus   status;

    public CommandSendPushStatus(PushStatus status){
        this.status = status;
    }

    public PushStatus getStatus() {
        return status;
    }

    @Override
    public CommandID getCommandID() {
        return CommandID.SEND_PUSH_STATUS;
    }

}
