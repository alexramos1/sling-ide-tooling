/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.ide.impl.vlt.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.sling.ide.transport.Batcher;
import org.apache.sling.ide.transport.Command;
import org.apache.sling.ide.util.PathUtil;

public class VltBatcher implements Batcher {
    
    private List<Command<?>> queue = new ArrayList<Command<?>>();
    
    @Override
    public void add(Command<?> command) {
        queue.add(command);
    }
    
    @Override
    public List<Command<?>> get() {

        LinkedCommands batched = new LinkedCommands();
        List<Command<?>> result = new ArrayList<Command<?>>();
        
        for ( Command<?> cmd : queue) {
            boolean accepted = batched.addLinked(cmd);
            if ( !accepted) {
                result.add(cmd);
            }
        }
        
        result.addAll(0, batched.getUpdates());
        result.addAll(0, batched.getDeletes());
        
        // Expected order is:
        // - delete
        // - add-or-update
        // - everything else, in the order it was specified
        
        queue.clear();
        
        return result;
    }
    
    private static class LinkedCommands {
        
        private List<Command<?>> deletes = new ArrayList<Command<?>>();
        private List<Command<?>> updates = new ArrayList<Command<?>>();
        
        public boolean addLinked(Command<?> newCmd) {
            
            if ( newCmd.getKind() == null ) {
                return false;
            }
            
            switch ( newCmd.getKind() ) {
                case DELETE:
                    processDelete(newCmd);
                    return true;
                    
                case ADD_OR_UPDATE:
                    processAddOrUpdate(newCmd);
                    return true;
                
                default:
                    return false;
            
            }
        }

        private void processDelete(Command<?> newCmd) {
            String path = newCmd.getPath();
            for ( ListIterator<Command<?>> iterator = deletes.listIterator(); iterator.hasNext(); ) {
                
                // if we already have an ancestor deleted, skip this one
                Command<?> oldCmd = iterator.next();
                if ( PathUtil.isAncestor(oldCmd.getPath(), path ) ) {
                    return;
                }
                
                // if we are delete an ancestor of another resource which gets deleted, replace it
                if ( PathUtil.isAncestor(path, oldCmd.getPath())) {
                    iterator.set(newCmd);
                    return;
                }
            }
            
            // no matching deletions, add it as-is
            deletes.add(newCmd);
        }
        
        private void processAddOrUpdate(Command<?> newCmd) {
            String path = newCmd.getPath();
            for (Command<?> oldCmd : updates) {
                // if we already have an add-or-update for this path, skip it    
                if ( path.equals(oldCmd.getPath()) ) {
                    return;
                }
            }
            
            // no adds or updates, add it as-is
            updates.add(newCmd);            
        }
        
        public List<Command<?>> getDeletes() {
            return deletes;
        }
        
        public List<Command<?>> getUpdates() {
            return updates;
        }
    }
}
