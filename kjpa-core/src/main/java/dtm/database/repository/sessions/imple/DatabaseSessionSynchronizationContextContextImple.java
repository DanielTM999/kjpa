package dtm.database.repository.sessions.imple;

import dtm.database.repository.sessions.DatabaseSession;
import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;

import java.util.HashMap;
import java.util.Map;

public class DatabaseSessionSynchronizationContextContextImple implements DatabaseSessionSynchronizationContext {

    private static final ThreadLocal<Map<Class<?>, DatabaseSession>> SESSION_STORAGE = ThreadLocal.withInitial(HashMap::new);

    @Override
    public boolean hasSession() {
        DatabaseSession databaseSession = getSession();
        if(databaseSession == null) return false;
        return databaseSession.isValidSession();
    }

    @Override
    public DatabaseSession getSession() {
        Class<?> caller = getCaller();
        return SESSION_STORAGE.get().get(caller);
    }

    @Override
    public void removeSession() {
        Class<?> caller = getCaller();
        Map<Class<?>, DatabaseSession> storage = SESSION_STORAGE.get();

        DatabaseSession session = storage.remove(caller);

        if (session != null) {
            session.rollbackIfActive();
            session.close();
        }

        if (session != null && storage.isEmpty()) {
            SESSION_STORAGE.remove();
        }
    }

    @Override
    public void addSession(DatabaseSession databaseSession, Class<?> owners) {
        Map<Class<?>, DatabaseSession> storage = SESSION_STORAGE.get();
        storage.put(getCaller(), databaseSession);
        if (owners != null) {
            storage.put(owners, databaseSession);
        }
    }

    private Class<?> getCaller(){
        Class<?> currentClass = this.getClass();
        return StackWalker
                .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> {
                    return frames.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(clazz -> !clazz.equals(currentClass))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Falha na sincronizacao: Nao foi possivel identificar a origem da chamada (Caller Class).")
                    );
                });
    }
}
