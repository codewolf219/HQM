package hardcorequesting.common.quests.task;

import hardcorequesting.common.client.interfaces.edit.GuiEditMenuItem;
import hardcorequesting.common.event.EventTrigger;
import hardcorequesting.common.quests.Quest;
import hardcorequesting.common.quests.data.QuestDataTaskItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class QuestTaskItemsCrafting extends QuestTaskItems {
    
    public QuestTaskItemsCrafting(Quest parent, String description, String longDescription) {
        super(parent, description, longDescription);
        register(EventTrigger.Type.CRAFTING);
    }
    
    @Override
    public void onUpdate(Player player) {
        
    }
    
    @Override
    public void onCrafting(Player player, ItemStack stack) {
        create(player, stack);
    }
    
    @Environment(EnvType.CLIENT)
    @Override
    protected GuiEditMenuItem.Type getMenuTypeId() {
        return GuiEditMenuItem.Type.CRAFTING_TASK;
    }
    
    private void create(Player player, ItemStack stack) {
        if (!player.getCommandSenderWorld().isClientSide) {
            if (!stack.isEmpty()) {
                //no need for the quest to be active
                //if (parent.isVisible(player) && parent.isEnabled(player) && isVisible(player)) {
                stack = stack.copy();
                if (stack.getCount() == 0) {
                    stack.setCount(1);
                }
                NonNullList<ItemStack> list = NonNullList.create();
                list.add(stack);
                increaseItems(list, (QuestDataTaskItems) getData(player), player.getUUID());
                //}
            }
        }
    }
    
    
}
