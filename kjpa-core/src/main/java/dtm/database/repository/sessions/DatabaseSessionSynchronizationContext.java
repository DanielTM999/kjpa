package dtm.database.repository.sessions;

public interface DatabaseSessionSynchronizationContext {
    boolean hasSession();
    DatabaseSession getSession();
    void removeSession();
    void addSession(DatabaseSession databaseSession, Class<?> owners);
}
