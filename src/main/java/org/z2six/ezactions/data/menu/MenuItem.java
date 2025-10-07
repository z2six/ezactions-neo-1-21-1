// MainFile: src/main/java/org/z2six/ezactions/data/menu/MenuItem.java
package org.z2six.ezactions.data.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.z2six.ezactions.Constants;
import org.z2six.ezactions.data.click.IClickAction;
import org.z2six.ezactions.data.icon.IconSpec;
import org.z2six.ezactions.data.json.ClickActionSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable-ish menu entry: either an action (has IClickAction, children empty)
 * or a category/page (action == null, may have children).
 *
 * IMPORTANT:
 *  - children is backed by a mutable list so the editor can mutate it in-place
 *    via childrenMutable(). This fixes "adding inside a category does nothing".
 *  - children() returns an unmodifiable view for read-only use.
 *
 * Notes:
 *  - Both actions and categories can carry an optional "note" string.
 */
public final class MenuItem {

    private final String id;
    private final String title;         // plain label
    private final String note;          // optional (actions and categories)
    private final IconSpec icon;        // visual icon spec
    private final IClickAction action;  // null => category
    private final List<MenuItem> children; // backing, mutable list for categories

    public MenuItem(String id,
                    String title,
                    String note,
                    IconSpec icon,
                    IClickAction action,
                    List<MenuItem> children) {
        this.id = Objects.requireNonNullElse(id, "item_" + Long.toUnsignedString(System.nanoTime(), 36));
        this.title = Objects.requireNonNullElse(title, "Unnamed");
        this.note = (note == null) ? "" : note; // keep as provided for both actions & categories
        this.icon = icon == null ? IconSpec.item("minecraft:stone") : icon;
        this.action = action; // nullable => category

        // Backing, MUTABLE list (no unmodifiable wrapper here!)
        if (children == null) {
            this.children = new ArrayList<>();
        } else {
            this.children = new ArrayList<>(children);
        }
    }

    // Backward-compat constructor (no note provided) – used by older callsites.
    public MenuItem(String id,
                    String title,
                    IconSpec icon,
                    IClickAction action,
                    List<MenuItem> children) {
        this(id, title, "", icon, action, children);
    }

    // -------- Accessors --------
    public String id() { return id; }
    public String title() { return title; }
    /** Returns the note (empty string if none). */
    public String note() { return note; }
    public IconSpec icon() { return icon; }
    public IClickAction action() { return action; }
    // Alias retained for older call sites:
    public IClickAction clickAction() { return action; }

    /** Read-only children view. */
    public List<MenuItem> children() {
        return Collections.unmodifiableList(children);
    }

    /** Editor/RadialMenu needs to mutate the actual list. */
    public List<MenuItem> childrenMutable() {
        return children; // <-- backing list, not a copy
    }

    public boolean isCategory() {
        return action == null;
    }

    // -------- Updaters --------

    /** Return a copy with a different icon. Never crashes. */
    public MenuItem withIcon(IconSpec newIcon) {
        try {
            IconSpec use = (newIcon == null) ? IconSpec.item("minecraft:stone") : newIcon;
            // keep current children (backing list) when copying
            return new MenuItem(this.id, this.title, this.note, use, this.action, this.children);
        } catch (Throwable t) {
            Constants.LOG.warn("[{}] MenuItem.withIcon failed: {}", Constants.MOD_NAME, t.toString());
            return this;
        }
    }

    /** Return a copy with a different title. */
    public MenuItem withTitle(String newTitle) {
        String use = (newTitle == null || newTitle.isBlank()) ? this.title : newTitle;
        return new MenuItem(this.id, use, this.note, this.icon, this.action, this.children);
    }

    /** Return a copy with a different note. */
    public MenuItem withNote(String newNote) {
        String use = (newNote == null) ? "" : newNote;
        return new MenuItem(this.id, this.title, use, this.icon, this.action, this.children);
    }

    /** Return a copy with a different action (converts category->action if non-null). */
    public MenuItem withAction(IClickAction newAction) {
        // when this becomes an action, children should be empty; preserve note
        return new MenuItem(this.id, this.title, this.note, this.icon, newAction, Collections.emptyList());
    }

    /** Return a copy with different children (converts to category; preserve note). */
    public MenuItem withChildren(List<MenuItem> newChildren) {
        return new MenuItem(this.id, this.title, this.note, this.icon, null, newChildren);
    }

    // -------- JSON (de)serialization --------

    /** Serialize to JSON used by MenuLoader. */
    public JsonObject serialize() {
        JsonObject o = new JsonObject();
        try {
            o.addProperty("id", this.id);
            o.addProperty("title", this.title);
            // store icon id (string) — IconSpec.item(id) can restore
            String iconId = "minecraft:stone";
            try { iconId = this.icon.id(); } catch (Throwable ignored) {}
            o.addProperty("icon", iconId);

            // Optional note for both actions and categories
            if (this.note != null && !this.note.isEmpty()) {
                o.addProperty("note", this.note);
            }

            if (this.action != null) {
                // Action object
                o.add("action", ClickActionSerializer.serialize(this.action));
            } else {
                // Category children
                JsonArray arr = new JsonArray();
                for (MenuItem child : this.children) {
                    arr.add(child.serialize());
                }
                o.add("children", arr);
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[{}] MenuItem.serialize failed: {}", Constants.MOD_NAME, t.toString());
        }
        return o;
    }

    /** Deserialize from JSON used by MenuLoader. */
    public static MenuItem deserialize(JsonObject o) {
        try {
            String id = getString(o, "id", "item_" + Long.toUnsignedString(System.nanoTime(), 36));
            String title = getString(o, "title", "Unnamed");
            String iconId = getString(o, "icon", "minecraft:stone");
            String note = getString(o, "note", ""); // read note for both cases

            IClickAction action = null;
            List<MenuItem> children = Collections.emptyList();

            if (o.has("action") && o.get("action").isJsonObject()) {
                action = org.z2six.ezactions.data.json.ClickActionSerializer.deserialize(o.getAsJsonObject("action"));
            } else if (o.has("children") && o.get("children").isJsonArray()) {
                List<MenuItem> list = new ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("children")) {
                    if (el.isJsonObject()) {
                        list.add(deserialize(el.getAsJsonObject()));
                    }
                }
                children = list;
            }

            return new MenuItem(id, title, note, IconSpec.item(iconId), action, children);
        } catch (Throwable t) {
            Constants.LOG.warn("[{}] MenuItem.deserialize failed: {}", Constants.MOD_NAME, t.toString());
            // return a safe placeholder so the menu keeps working
            return new MenuItem("invalid", "Invalid", "", IconSpec.item("minecraft:barrier"), null, Collections.emptyList());
        }
    }

    private static String getString(JsonObject o, String key, String def) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                return o.get(key).getAsString();
            }
        } catch (Throwable ignored) {}
        return def;
    }

    // -------- factories --------

    /** Create an action item. */
    public static MenuItem action(String id, String title, String note, IconSpec icon, IClickAction act) {
        return new MenuItem(id, title, note, icon, act, Collections.emptyList());
    }

    /** Create a category item (page). Note: callers that care about notes can use the main constructor. */
    public static MenuItem category(String id, String title, IconSpec icon, List<MenuItem> children) {
        return new MenuItem(id, title, "", icon, null, children);
    }

    @Override
    public String toString() {
        int childCount = (children == null) ? 0 : children.size();
        return "MenuItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", noteLen=" + (note == null ? 0 : note.length()) +
                ", icon=" + (icon == null ? "null" : icon.id()) +
                ", action=" + (action == null ? "<category>" : action.getType()) +
                ", children=" + childCount +
                '}';
    }
}
