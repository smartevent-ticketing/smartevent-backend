package com.smartevent.ticketing.modules.identity.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "organizer_profiles")
public class OrganizerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "id",
            nullable = false,
            unique = true
    )
    private User user;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "business_address", columnDefinition = "TEXT")
    private String businessAddress;

    @Column(name = "bank_account_status", nullable = false, length = 50)
    private String bankAccountStatus = "PENDING";

    public OrganizerProfile(User user, String companyName, String taxCode, String businessAddress) {
        this.user = user;
        this.companyName = companyName;
        this.taxCode = taxCode;
        this.businessAddress = businessAddress;
        this.bankAccountStatus = "PENDING";
    }
}
