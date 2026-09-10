package com.smartevent.modules.event.service;

import java.util.UUID;

/** Synchronous application event; cleanup participates in the cancellation transaction. */
public record EventCancelled(UUID eventId, String reason) {}
