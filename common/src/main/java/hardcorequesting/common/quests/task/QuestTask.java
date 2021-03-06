package hardcorequesting.common.quests.task;


import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.mojang.blaze3d.vertex.PoseStack;
import hardcorequesting.common.client.ClientChange;
import hardcorequesting.common.client.interfaces.GuiBase;
import hardcorequesting.common.client.interfaces.GuiQuestBook;
import hardcorequesting.common.client.sounds.Sounds;
import hardcorequesting.common.event.EventTrigger;
import hardcorequesting.common.io.adapter.Adapter;
import hardcorequesting.common.io.adapter.QuestTaskAdapter;
import hardcorequesting.common.network.NetworkManager;
import hardcorequesting.common.quests.*;
import hardcorequesting.common.quests.data.QuestDataTask;
import hardcorequesting.common.team.RewardSetting;
import hardcorequesting.common.team.TeamLiteStat;
import hardcorequesting.common.util.Translator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;

public abstract class QuestTask {
    
    static final int START_X = 180;
    static final int START_Y = 95;
    public String description;
    protected Quest parent;
    private List<QuestTask> requirements;
    private String longDescription;
    private int id;
    private List<FormattedText> cachedDescription;
    
    public QuestTask(Quest parent, String description, String longDescription) {
        this.parent = parent;
        this.requirements = new ArrayList<>();
        this.description = description;
        this.longDescription = longDescription;
        updateId();
    }
    
    public static void completeQuest(Quest quest, UUID uuid) {
        if (!quest.isEnabled(uuid) || !quest.isAvailable(uuid)) return;
        for (QuestTask questTask : quest.getTasks()) {
            if (!questTask.getData(uuid).completed) {
                return;
            }
        }
        QuestData data = quest.getQuestData(uuid);
        
        data.completed = true;
        data.claimed = false;
        data.available = false;
        data.time = Quest.serverTicker.getHours();
        
        
        if (QuestingDataManager.getInstance().getQuestingData(uuid).getTeam().getRewardSetting() == RewardSetting.RANDOM) {
            int rewardId = (int) (Math.random() * data.reward.length);
            data.reward[rewardId] = true;
        } else {
            for (int i = 0; i < data.reward.length; i++) {
                data.reward[i] = true;
            }
        }
        quest.sendUpdatedDataToTeam(uuid);
        TeamLiteStat.refreshTeam(QuestingDataManager.getInstance().getQuestingData(uuid).getTeam());
        
        for (Quest child : quest.getReversedRequirement()) {
            completeQuest(child, uuid);
            child.sendUpdatedDataToTeam(uuid);
        }
        
        if (quest.getRepeatInfo().getType() == RepeatType.INSTANT) {
            quest.reset(uuid);
        }
        
        Player player = QuestingData.getPlayer(uuid);
        if (player instanceof ServerPlayer && !quest.hasReward(player)) {
            // when there is no reward and it just completes the quest play the music
            NetworkManager.sendToPlayer(ClientChange.SOUND.build(Sounds.COMPLETE), (ServerPlayer) player);
        }
        
        if (player != null) {
            EventTrigger.instance().onQuestComplete(new EventTrigger.QuestCompletedEvent(player, quest.getQuestId()));
        }
    }
    
    public abstract void write(Adapter.JsonObjectBuilder builder);
    
    public abstract void read(JsonObject object);
    
    public void updateId() {
        this.id = parent.nextTaskId++;
    }
    
    public boolean allowManual() {
        return false;
    }
    
    public boolean allowDetect() {
        return false;
    }
    
    public boolean isCompleted(Player player) {
        return getData(player).completed;
    }
    
    public boolean isCompleted(UUID uuid) {
        return getData(uuid).completed;
    }
    
    public boolean isVisible(Player player) {
        Iterator<QuestTask> itr = this.requirements.iterator();
        QuestTask requirement;
        do {
            if (!itr.hasNext()) return true;
            requirement = itr.next();
        } while (requirement.isCompleted(player) && requirement.isVisible(player));
        return false;
    }
    
    public Class<? extends QuestDataTask> getDataType() {
        return QuestDataTask.class;
    }
    
    public void write(QuestDataTask task, JsonObject out) {
        task.write(new Adapter.JsonObjectBuilder(out));
    }
    
    public void read(QuestDataTask task, JsonReader in) throws IOException {
        task.update(QuestTaskAdapter.QUEST_DATA_TASK_ADAPTER.read(in));
    }
    
    public QuestDataTask getData(Player player) {
        return getData(player.getUUID());
    }
    
    public QuestDataTask getData(UUID uuid) {
        if (this.id < 0) {
            return newQuestData(); // possible fix for #247
        }
        QuestData questData = QuestingDataManager.getInstance().getQuestingData(uuid).getQuestData(parent.getQuestId());
        if (id >= questData.tasks.length) {
            questData.tasks = Arrays.copyOf(questData.tasks, id + 1);
            questData.tasks[id] = newQuestData();
        }
        return questData.tasks[id] = validateData(questData.tasks[id]);
    }
    
    public QuestDataTask validateData(QuestDataTask data) {
        if (data == null || data.getClass() != getDataType()) {
            return newQuestData();
        }
        
        return data;
    }
    
    private QuestDataTask newQuestData() {
        try {
            Constructor<? extends QuestDataTask> constructor = getDataType().getConstructor(QuestTask.class);
            Object obj = constructor.newInstance(this);
            return (QuestDataTask) obj;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    public String getLangKeyDescription() {
        return description;
    }
    
    public String getDescription() {
        return Translator.get(description);
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getLangKeyLongDescription() {
        return longDescription;
    }
    
    public String getLongDescription() {
        return Translator.get(longDescription);
    }
    
    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
        cachedDescription = null;
    }
    
    @Environment(EnvType.CLIENT)
    public List<FormattedText> getCachedLongDescription(GuiBase gui) {
        if (cachedDescription == null) {
            cachedDescription = gui.getLinesFromText(Translator.translatable(longDescription), 0.7F, 130);
        }
        
        return cachedDescription;
    }
    
    public void completeTask(UUID uuid) {
        getData(uuid).completed = true;
        completeQuest(parent, uuid);
    }
    
    @Environment(EnvType.CLIENT)
    public abstract void draw(PoseStack matrices, GuiQuestBook gui, Player player, int mX, int mY);
    
    @Environment(EnvType.CLIENT)
    public abstract void onClick(GuiQuestBook gui, Player player, int mX, int mY, int b);
    
    public abstract void onUpdate(Player player);
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public Quest getParent() {
        return parent;
    }
    
    public List<QuestTask> getRequirements() {
        return requirements;
    }
    
    public void addRequirement(QuestTask task) {
        requirements.add(task);
    }
    
    public void clearRequirements() {
        requirements.clear();
    }
    
    public abstract float getCompletedRatio(UUID uuid);
    
    public abstract void mergeProgress(UUID playerId, QuestDataTask own, QuestDataTask other);
    
    public void autoComplete(UUID playerId) {
        autoComplete(playerId, true);
    }
    
    public void uncomplete(UUID playerId) {
    }
    
    public abstract void autoComplete(UUID playerId, boolean status);
    
    public void copyProgress(QuestDataTask own, QuestDataTask other) {
        own.completed = other.completed;
    }
    
    public void onDelete() {
        EventTrigger.instance().remove(this);
    }
    
    public void register(EventTrigger.Type... types) {
        EventTrigger.instance().add(this, types);
    }
    
    public boolean isValid() {
        return getParent() != null && getParent().getTasks() != null && getParent().getTasks().contains(this);
    }
    
    //for these to be called one must register the task using the method above using the correct types
    public void onServerTick(MinecraftServer server) {
    }
    
    public void onPlayerTick(ServerPlayer playerEntity) {
    }
    
    public void onLivingDeath(LivingEntity entity, DamageSource source) {
    }
    
    public void onCrafting(Player player, ItemStack stack) {
    }
    
    public void onItemPickUp(Player playerEntity, ItemStack stack) {
    }
    
    public void onOpenBook(EventTrigger.BookOpeningEvent event) {
    }
    
    public void onReputationChange(EventTrigger.ReputationEvent event) {
    }
    
    public void onAnimalTame(Player tamer, Entity entity) {
    }
    
    public void onAdvancement(ServerPlayer playerEntity) {
    }
    
    public void onQuestCompleted(EventTrigger.QuestCompletedEvent event) {
    }
    
    public void onQuestSelected(EventTrigger.QuestSelectedEvent event) {
    }
    
    public void onBlockPlaced(Level world, BlockState state, LivingEntity entity) {
    }
    
    public void onBlockBroken(BlockPos blockPos, BlockState blockState, Player player) {
    }
    
    public void onItemUsed(Player playerEntity, Level world, InteractionHand hand) {
    }
    
    public void onBlockUsed(Player playerEntity, Level world, InteractionHand hand) {
    }
}
