package com.gielinordailies;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class GielinorDailiesApiClient
{
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final GielinorDailiesConfig config;

    @Inject
    public GielinorDailiesApiClient(OkHttpClient httpClient, Gson gson, GielinorDailiesConfig config)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        this.config = config;
    }

    private String baseUrl()
    {
        String url = config.apiUrl();
        if (url.endsWith("/"))
        {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private Request.Builder authedRequest(String path)
    {
        return new Request.Builder()
            .url(baseUrl() + path)
            .header("Authorization", "Bearer " + config.apiToken())
            .header("User-Agent", "GielinorDailies-RuneLite/1.0");
    }

    /**
     * Test connectivity and token validity.
     */
    public boolean testConnection()
    {
        try
        {
            Request request = authedRequest("/api/characters").get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies connection test failed", e);
            return false;
        }
    }

    /**
     * Fetch the list of characters.
     */
    public List<GielinorDailiesCharacter> getCharacters()
    {
        try
        {
            Request request = authedRequest("/api/characters").get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    log.warn("Gielinor Dailies: Failed to fetch characters: {}", response.code());
                    return new ArrayList<>();
                }

                String bodyStr = response.body().string();
                JsonObject json = new JsonParser().parse(bodyStr).getAsJsonObject();
                JsonArray chars = json.getAsJsonArray("characters");
                List<GielinorDailiesCharacter> result = new ArrayList<>();

                for (JsonElement el : chars)
                {
                    JsonObject c = el.getAsJsonObject();
                    result.add(new GielinorDailiesCharacter(
                        c.get("id").getAsInt(),
                        c.get("name").getAsString(),
                        c.get("account_type").getAsString(),
                        c.get("data_source").getAsString()
                    ));
                }
                log.debug("Gielinor Dailies: Fetched {} characters", result.size());
                return result;
            }
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Error fetching characters", e);
            return new ArrayList<>();
        }
    }

    /**
     * Push skill stats and boss KC for a character.
     */
    public boolean pushStats(int characterId, Map<String, SkillData> skills, Map<String, Integer> bosses, int combatLevel)
    {
        try
        {
            JsonObject payload = new JsonObject();
            payload.addProperty("character_id", characterId);
            payload.addProperty("combat_level", combatLevel);

            JsonObject skillsObj = new JsonObject();
            for (Map.Entry<String, SkillData> entry : skills.entrySet())
            {
                JsonObject skill = new JsonObject();
                skill.addProperty("level", entry.getValue().level);
                skill.addProperty("xp", entry.getValue().xp);
                skill.addProperty("rank", entry.getValue().rank);
                skillsObj.add(entry.getKey(), skill);
            }
            payload.add("skills", skillsObj);

            JsonObject bossesObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : bosses.entrySet())
            {
                bossesObj.addProperty(entry.getKey(), entry.getValue());
            }
            payload.add("bosses", bossesObj);

            RequestBody body = RequestBody.create(JSON_MEDIA, gson.toJson(payload));
            Request request = authedRequest("/api/stats/push").post(body).build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful())
                {
                    log.warn("Gielinor Dailies stats push failed: {}", response.code());
                    return false;
                }
                return true;
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies stats push error", e);
            return false;
        }
    }

    /**
     * Push XP/KC gains data for Recent Activity.
     */
    public boolean pushGains(int characterId, Map<String, Long> xpGains, Map<String, Integer> kcGains)
    {
        try
        {
            JsonObject payload = new JsonObject();
            payload.addProperty("character_id", characterId);

            JsonObject gains = new JsonObject();

            JsonObject todayObj = new JsonObject();
            JsonObject todayXp = new JsonObject();
            for (Map.Entry<String, Long> entry : xpGains.entrySet())
            {
                if (entry.getValue() > 0)
                {
                    todayXp.addProperty(entry.getKey(), entry.getValue());
                }
            }
            todayObj.add("xp", todayXp);

            JsonObject todayKc = new JsonObject();
            for (Map.Entry<String, Integer> entry : kcGains.entrySet())
            {
                if (entry.getValue() > 0)
                {
                    todayKc.addProperty(entry.getKey(), entry.getValue());
                }
            }
            todayObj.add("kc", todayKc);

            gains.add("today", todayObj);
            payload.add("gains", gains);

            RequestBody body = RequestBody.create(JSON_MEDIA, gson.toJson(payload));
            Request request = authedRequest("/api/stats/gains").post(body).build();

            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies gains push error", e);
            return false;
        }
    }

    /**
     * Fetch today's tasks (optionally for a specific character).
     */
    public List<GielinorDailiesTask> getTodayTasks(Integer characterId)
    {
        try
        {
            String path = "/api/tasks/today";
            if (characterId != null)
            {
                path += "?character_id=" + characterId;
            }

            Request request = authedRequest(path).get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return new ArrayList<>();
                }

                JsonObject json = new JsonParser().parse(response.body().string()).getAsJsonObject();
                JsonArray tasks = json.getAsJsonArray("tasks");
                List<GielinorDailiesTask> result = new ArrayList<>();

                for (JsonElement el : tasks)
                {
                    JsonObject t = el.getAsJsonObject();
                    result.add(new GielinorDailiesTask(
                        t.get("id").getAsInt(),
                        t.get("title").getAsString(),
                        t.get("task_type").getAsString(),
                        t.get("priority").getAsString(),
                        t.get("character_name").getAsString(),
                        t.get("is_completed").getAsBoolean()
                    ));
                }
                return result;
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch tasks", e);
            return new ArrayList<>();
        }
    }

    /**
     * Lightweight change-check: returns a hash of task state so the plugin can
     * skip a full fetch when nothing has changed.
     * Returns null on error.
     */
    public TaskCheckResult checkTasks(Integer characterId)
    {
        try
        {
            String path = "/api/tasks/check";
            if (characterId != null)
            {
                path += "?character_id=" + characterId;
            }

            Request request = authedRequest(path).get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return null;
                }

                JsonObject json = new JsonParser().parse(response.body().string()).getAsJsonObject();
                return new TaskCheckResult(
                    json.get("hash").getAsString(),
                    json.get("total").getAsInt(),
                    json.get("completed").getAsInt()
                );
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to check tasks", e);
            return null;
        }
    }

    /**
     * Simple data holder for task check response.
     */
    public static class TaskCheckResult
    {
        public final String hash;
        public final int total;
        public final int completed;

        public TaskCheckResult(String hash, int total, int completed)
        {
            this.hash = hash;
            this.total = total;
            this.completed = completed;
        }
    }

    /**
     * Push quest states for a character.
     */
    public boolean pushQuests(int characterId, List<QuestTracker.QuestData> quests, int totalQuestPoints)
    {
        try
        {
            JsonObject payload = new JsonObject();
            payload.addProperty("character_id", characterId);
            payload.addProperty("quest_points", totalQuestPoints);

            JsonArray questArray = new JsonArray();
            for (QuestTracker.QuestData q : quests)
            {
                JsonObject questObj = new JsonObject();
                questObj.addProperty("name", q.name);
                questObj.addProperty("state", q.state);
                questArray.add(questObj);
            }
            payload.add("quests", questArray);

            RequestBody body = RequestBody.create(JSON_MEDIA, gson.toJson(payload));
            Request request = authedRequest("/api/quests/push").post(body).build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful())
                {
                    log.warn("Gielinor Dailies quest push failed: {}", response.code());
                    return false;
                }
                return true;
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies quest push error", e);
            return false;
        }
    }

    /**
     * Fetch active announcements from the server.
     */
    public List<GielinorDailiesAnnouncement> getAnnouncements()
    {
        try
        {
            Request request = authedRequest("/api/announcements").get().build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return new ArrayList<>();
                }

                JsonObject json = new JsonParser().parse(response.body().string()).getAsJsonObject();
                JsonArray arr = json.getAsJsonArray("announcements");
                List<GielinorDailiesAnnouncement> result = new ArrayList<>();

                for (JsonElement el : arr)
                {
                    JsonObject a = el.getAsJsonObject();
                    result.add(new GielinorDailiesAnnouncement(
                        a.get("id").getAsInt(),
                        a.get("title").getAsString(),
                        a.get("body").getAsString(),
                        a.get("type").getAsString(),
                        a.has("is_pinned") && a.get("is_pinned").getAsInt() == 1
                    ));
                }
                return result;
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch announcements", e);
            return new ArrayList<>();
        }
    }

    /**
     * Dismiss an announcement for the current user.
     */
    public boolean dismissAnnouncement(int announcementId)
    {
        try
        {
            RequestBody body = RequestBody.create(JSON_MEDIA, "{}");
            Request request = authedRequest("/api/announcements/" + announcementId + "/dismiss").post(body).build();
            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies announcement dismiss error", e);
            return false;
        }
    }

    /**
     * Mark a task as completed.
     */
    public boolean completeTask(int taskId)
    {
        try
        {
            RequestBody body = RequestBody.create(JSON_MEDIA, "{}");
            Request request = authedRequest("/api/tasks/" + taskId + "/complete").post(body).build();
            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies task complete error", e);
            return false;
        }
    }

    /**
     * Undo a task completion (uncomplete).
     */
    public boolean uncompleteTask(int taskId)
    {
        try
        {
            RequestBody body = RequestBody.create(JSON_MEDIA, "{}");
            Request request = authedRequest("/api/tasks/" + taskId + "/uncomplete").post(body).build();
            try (Response response = httpClient.newCall(request).execute())
            {
                return response.isSuccessful();
            }
        }
        catch (IOException e)
        {
            log.warn("Gielinor Dailies task uncomplete error", e);
            return false;
        }
    }

    // --- Data classes ---

    public static class SkillData
    {
        public final int level;
        public final long xp;
        public final int rank;

        public SkillData(int level, long xp, int rank)
        {
            this.level = level;
            this.xp = xp;
            this.rank = rank;
        }
    }

    public static class GielinorDailiesCharacter
    {
        public final int id;
        public final String name;
        public final String accountType;
        public final String dataSource;

        public GielinorDailiesCharacter(int id, String name, String accountType, String dataSource)
        {
            this.id = id;
            this.name = name;
            this.accountType = accountType;
            this.dataSource = dataSource;
        }
    }

    public static class GielinorDailiesTask
    {
        public final int id;
        public final String title;
        public final String taskType;
        public final String priority;
        public final String characterName;
        public final boolean completed;

        public GielinorDailiesTask(int id, String title, String taskType, String priority, String characterName, boolean completed)
        {
            this.id = id;
            this.title = title;
            this.taskType = taskType;
            this.priority = priority;
            this.characterName = characterName;
            this.completed = completed;
        }
    }

    public static class GielinorDailiesAnnouncement
    {
        public final int id;
        public final String title;
        public final String body;
        public final String type;
        public final boolean pinned;

        public GielinorDailiesAnnouncement(int id, String title, String body, String type, boolean pinned)
        {
            this.id = id;
            this.title = title;
            this.body = body;
            this.type = type;
            this.pinned = pinned;
        }
    }
}
