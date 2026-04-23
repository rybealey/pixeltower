package org.pixeltower.rp.functional;

/**
 * One row from {@code rp_functional_furniture}, hydrated.
 *
 * @param itemBaseId    items_base.id (the catalog row, not a placed instance)
 * @param triggerType   when this fires (walk-on vs click)
 * @param actionType    free-form action key, switched on by
 *                      {@link FunctionalActionDispatcher}
 * @param payload       opaque per-action argument (e.g. the link suffix
 *                      "show" / "toggle" for open_avatar_editor)
 * @param cooldownMs    minimum gap between consecutive fires of this row
 *                      for the same (player, placed-furni) pair
 */
public record FunctionalAction(int itemBaseId,
                               TriggerType triggerType,
                               String actionType,
                               String payload,
                               int cooldownMs) {
}
