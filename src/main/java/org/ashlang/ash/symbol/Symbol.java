/*
 * The Ash Project
 * Copyright (C) 2017  Peter Skrypalle
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ashlang.ash.symbol;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.ashlang.ash.ast.DeclarationNode;
import org.ashlang.ash.type.Type;

public class Symbol {

    private final DeclarationNode declSite;

    private boolean isInitialized;
    private boolean isUsed;

    Symbol(DeclarationNode declSite) {
        this.declSite = declSite;
    }

    public DeclarationNode
    getDeclSite() {
        return declSite;
    }

    public String
    getIdentifier() {
        return declSite.getIdentifierToken().getText();
    }

    public Type
    getType() {
        return declSite.getType();
    }

    public void
    initialize() {
        isInitialized = true;
    }

    public void
    deinitialize() {
        isInitialized = false;
    }

    public boolean
    isInitialized() {
        return isInitialized;
    }

    public void
    use() {
        isUsed = true;
    }

    public boolean
    isUsed() {
        return isUsed;
    }

    @Override
    public String
    toString() {
        return new ToStringBuilder(this, ToStringStyle.JSON_STYLE)
            .append("identifier", getIdentifier())
            .append("type", getType())
            .build();
    }

}
