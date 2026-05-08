package com.service;

import com.common.config.LocalStorageProperties;
import com.persistence.local.mapper.contactsRecord.BlacklistMapper;
import com.persistence.local.mapper.contactsRecord.ContactMapper;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import com.persistence.local.model.contactsRecord.ContactRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class LocalContactBookService
{
    private final ContactMapper contactMapper;
    private final BlacklistMapper blacklistMapper;

    public LocalContactBookService(BlacklistMapper blacklistMapper, ContactMapper contactMapper) {
        this.blacklistMapper = blacklistMapper;
        this.contactMapper = contactMapper;
    }

    @PostConstruct
    public void initTables()
    {
        contactMapper.createTableIfNotExists();
        blacklistMapper.createTableIfNotExists();
    }

    public ContactRecord addContact(String accountId, String publicKey, String alias)
    {
        validateAccountId(accountId);

        ContactRecord existing=contactMapper.findByAccountId(accountId);
        String now= Instant.now().toString();
        String normalizedPublicKey = normalizeBlank(publicKey);

        if(existing!=null)
        {
            existing.setAlias(normalizeBlank(alias));
            if(normalizedPublicKey!=null)
            {
                existing.setPublicKey(normalizedPublicKey);
            }
            existing.setUpdatedAt(now);
            contactMapper.updateByAccountId(existing);
            return contactMapper.findByAccountId(accountId);
        }

        ContactRecord record=new ContactRecord();
        record.setContactIndex(contactMapper.nextContactIndex());
        record.setAlias(normalizeBlank(alias));
        record.setAccountId(accountId);
        record.setPublicKey(normalizedPublicKey);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        contactMapper.insert(record);
        return contactMapper.findByAccountId(accountId);
    }

    public List<ContactRecord> listContacts()
    {
        return contactMapper.findAll();
    }

    public Optional<ContactRecord> findContactByIndex(int contactIndex)
    {
        return Optional.ofNullable(contactMapper.findByContactIndex(contactIndex));
    }

    public Optional<ContactRecord> findContactByAccountId(String accountId)
    {
        return Optional.ofNullable(contactMapper.findByAccountId(accountId));
    }

    public void removeContactByIndex(int contactIndex)
    {
        int res=contactMapper.deleteByContactIndex(contactIndex);
        if(res==0)
        {
            throw new IllegalArgumentException("Contact not found: contact-"+contactIndex);
        }
    }

    public String resolveAccountId(String value)
    {
        Optional<Integer>contactIndex=parseContactToken(value);
        if(contactIndex.isEmpty())
        {
            return value;
        }

        ContactRecord contact=contactMapper.findByContactIndex(contactIndex.get());
        if(contact==null)
        {
            throw new IllegalArgumentException("Contact not found: "+value);
        }

        return contact.getAccountId();
    }

    public BlacklistRecord addBlacklist(String accountId, String publicKey,  String reason)
    {
        validateAccountId(accountId);

        BlacklistRecord record=new BlacklistRecord();
        record.setAccountId(accountId);
        record.setPublicKey(publicKey);
        record.setReason(normalizeBlank(reason));
        record.setCreatedAt(Instant.now().toString());

        blacklistMapper.upsert(record);
        return blacklistMapper.findByAccountId(accountId);
    }

    public BlacklistRecord addBlacklistByContactIndex(int contactIndex, String reason)
    {
        ContactRecord contact=contactMapper.findByContactIndex(contactIndex);
        if(contact==null)
        {
            throw new IllegalArgumentException("Contact not found: contact-"+contactIndex);
        }

        return addBlacklist(contact.getAccountId(), contact.getPublicKey(), reason);
    }

    public List<BlacklistRecord> listBlacklist()
    {
        return blacklistMapper.findAll();
    }

    public Optional<BlacklistRecord> findBlacklist(String accountId)
    {
        return Optional.ofNullable(blacklistMapper.findByAccountId(accountId));
    }

    public boolean isBlacklisted(String accountId)
    {
        if(accountId==null || accountId.isBlank())
        {
            return false;
        }
        return blacklistMapper.findByAccountId(accountId)!=null;
    }

    public void removeBlacklist(String accountId)
    {
        int res=blacklistMapper.deleteByAccountId(accountId);
        if(res==0)
        {
            throw new IllegalArgumentException("Blacklist record not found: "+accountId);
        }
    }

    private Optional<Integer> parseContactToken(String value)
    {
        if(value==null || !value.matches("contact-\\d+"))
        {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value.substring("contact-".length())));
    }

    private void validateAccountId(String accountId)
    {
        if(accountId==null || accountId.isBlank())
        {
            throw new IllegalArgumentException("AccountId is required");
        }
    }

    private String normalizeBlank(String value)
    {
        if(value==null || value.isBlank())
        {
            return null;
        }
        return value;
    }
}
