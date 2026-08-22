package com.smartevent.modules.ticket.service;

import com.smartevent.modules.ticket.dto.request.CheckinRequest;
import com.smartevent.modules.ticket.dto.response.CheckinResponse;

import java.util.UUID;

public interface CheckinService {

    // Xử lý quét soát vé tại cổng với tốc độ cao, kiểm tra mã giả và chống quét trùng (Duplicate Guard)
    CheckinResponse processCheckin(CheckinRequest request, UUID staffUserId);
}