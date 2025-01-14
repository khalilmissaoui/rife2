/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.authentication.sessionmanagers;

import rife.authentication.ListSessions;
import rife.authentication.SessionManager;
import rife.authentication.exceptions.SessionManagerException;
import rife.authentication.sessionmanagers.exceptions.StartSessionErrorException;
import rife.config.RifeConfig;
import rife.tools.UniqueIDGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class MemorySessions implements SessionManager {
    private long sessionDuration_ = RifeConfig.authentication().getSessionDuration();
    private boolean restrictAuthData_ = RifeConfig.authentication().getSessionRestrictAuthData();
    private int sessionPurgeFrequency_ = RifeConfig.authentication().getSessionPurgeFrequency();
    private int sessionPurgeScale_ = RifeConfig.authentication().getSessionPurgeScale();

    private final ConcurrentHashMap<String, MemorySession> sessions_ = new ConcurrentHashMap<>();

    public MemorySessions() {
    }

    public long getSessionDuration() {
        return sessionDuration_;
    }

    public void setSessionDuration(long milliseconds) {
        sessionDuration_ = milliseconds;
    }

    public boolean getRestrictAuthData() {
        return restrictAuthData_;
    }

    public void setRestrictAuthData(boolean flags) {
        restrictAuthData_ = flags;
    }

    public int getSessionPurgeFrequency() {
        return sessionPurgeFrequency_;
    }

    public void setSessionPurgeFrequency(int frequency) {
        sessionPurgeFrequency_ = frequency;
    }

    public int getSessionPurgeScale() {
        return sessionPurgeScale_;
    }

    public void setSessionPurgeScale(int scale) {
        sessionPurgeScale_ = scale;
    }

    public void purgeSessions() {
        new PurgeSessions().start();
    }

    private class PurgeSessions extends Thread {
        public void run() {
            var expiration = System.currentTimeMillis() - getSessionDuration();
            sessions_.values().removeIf(session -> session.getStart() <= expiration);
        }
    }

    public String startSession(long userId, String authData, boolean remembered)
    throws SessionManagerException {
        if (userId < 0 ||
            null == authData ||
            0 == authData.length()) {
            throw new StartSessionErrorException(userId, authData);
        }

        int purge_decision = ThreadLocalRandom.current().nextInt(getSessionPurgeScale());
        if (purge_decision <= getSessionPurgeFrequency()) {
            purgeSessions();
        }

        var auth_id_string = UniqueIDGenerator.generate().toString();

        var session = new MemorySession(auth_id_string, userId, authData, remembered);
        sessions_.put(auth_id_string, session);

        return auth_id_string;
    }

    public boolean isSessionValid(String authId, String authData)
    throws SessionManagerException {
        if (null == authId ||
            0 == authId.length() ||
            null == authData ||
            0 == authData.length()) {
            return false;
        }

        var session = getSession(authId);

        if (session != null) {
            return session.getStart() > System.currentTimeMillis() - getSessionDuration() &&
                   (!restrictAuthData_ || session.getAuthData().equals(authData));
        }

        return false;
    }

    public long getSessionUserId(String authId)
    throws SessionManagerException {
        var session = sessions_.get(authId);

        if (null == session) {
            return -1;
        }

        return session.getUserId();
    }

    public boolean continueSession(String authId)
    throws SessionManagerException {
        if (null == authId ||
            0 == authId.length()) {
            return false;
        }

        return null != sessions_.computeIfPresent(authId, (s, session) -> {
            session.setStart(System.currentTimeMillis());
            return session;
        });
    }

    public boolean eraseSession(String authId)
    throws SessionManagerException {
        if (null == authId ||
            0 == authId.length()) {
            return false;
        }

        return sessions_.remove(authId) != null;
    }

    public boolean wasRemembered(String authId)
    throws SessionManagerException {
        if (null == authId ||
            0 == authId.length()) {
            return false;
        }

        var session = sessions_.get(authId);
        if (null == session) {
            return false;
        }

        return session.getRemembered();
    }

    public boolean eraseUserSessions(long userId)
    throws SessionManagerException {
        if (userId < 0) {
            return false;
        }

        return sessions_.values().removeIf(session -> userId == session.getUserId());
    }

    public void eraseAllSessions()
    throws SessionManagerException {
        sessions_.clear();
    }

    public MemorySession getSession(String authId) {
        return sessions_.get(authId);
    }

    public long countSessions() {
        var expiration = System.currentTimeMillis() - getSessionDuration();
        return sessions_.reduceToLong(1,
            (s, session) -> (session.getStart() > expiration ? 1L : 0L),
            0, Long::sum);
    }

    public boolean listSessions(ListSessions processor) {
        if (null == processor) throw new IllegalArgumentException("processor can't be null");

        var result = false;

        var expiration = System.currentTimeMillis() - getSessionDuration();
        for (var session : sessions_.values()) {
            if (session.getStart() > expiration) {
                result = true;
                if (!processor.foundSession(session.getUserId(), session.getAuthData(), session.getAuthId())) {
                    break;
                }
            }
        }

        return result;
    }
}

