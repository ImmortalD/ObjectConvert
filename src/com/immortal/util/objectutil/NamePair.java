package com.immortal.util.objectutil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Immortal
 * @version V1.0
 * @since 2016-10-11
 */
public class NamePair {
    private String oldName;
    private String newName;

    private NamePair() {
    }

    private NamePair(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getOldName() {
        return oldName;
    }

    public void setOldName(String oldName) {
        this.oldName = oldName;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    // -----------------------------
    //   builder
    // -----------------------------
    public static class Builder {

        private List<NamePair> list = new ArrayList<NamePair>();

        public Builder add(String oldName, String newName) {
            this.list.add(new NamePair(oldName, newName));
            return this;
        }

        public List<NamePair> build() {
            return this.list;
        }

    }
}