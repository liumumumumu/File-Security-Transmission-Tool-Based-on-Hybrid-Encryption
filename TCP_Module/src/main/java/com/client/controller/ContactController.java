package com.client.controller;

import com.client.service.ClientTransferService;
import com.client.service.LocalContactBookService;
import com.client.service.PublicKeyPayloadService;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.common.util.PathInputNormalizer;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import com.persistence.local.model.contactsRecord.ContactRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final LocalContactBookService localContactBookService;
    private final ClientTransferService clientTransferService;
    private final PublicKeyPayloadService publicKeyPayloadService;

    public ContactController(
            LocalContactBookService localContactBookService,
            ClientTransferService clientTransferService,
            PublicKeyPayloadService publicKeyPayloadService
    ) {
        this.localContactBookService = localContactBookService;
        this.clientTransferService = clientTransferService;
        this.publicKeyPayloadService = publicKeyPayloadService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listContacts() {
        List<ContactRecord> contacts = localContactBookService.listContacts();
        List<Map<String, Object>> result = contacts.stream()
                .map(contact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("contactIndex", contact.getContactIndex());
                    item.put("alias", contact.getAlias());
                    item.put("accountId", contact.getAccountId());
                    item.put("publicKey", contact.getPublicKey());
                    item.put("createdAt", contact.getCreatedAt());
                    item.put("updatedAt", contact.getUpdatedAt());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{contactIndex}")
    public ResponseEntity<Map<String, Object>> getContact(@PathVariable int contactIndex) {
        return localContactBookService.findContactByIndex(contactIndex)
                .map(contact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("contactIndex", contact.getContactIndex());
                    item.put("alias", contact.getAlias());
                    item.put("accountId", contact.getAccountId());
                    item.put("publicKey", contact.getPublicKey());
                    item.put("createdAt", contact.getCreatedAt());
                    item.put("updatedAt", contact.getUpdatedAt());
                    return ResponseEntity.ok(item);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addContact(@RequestBody Map<String, String> request) throws Exception {
        if (request == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "accountId is required");
            return ResponseEntity.badRequest().body(error);
        }
        String accountId = request.get("accountId");
        String alias = request.get("alias");
        String publicKey = request.get("publicKey");
        String publicKeyPath = request.get("publicKeyPath");

        if (publicKey != null && !publicKey.isBlank() && publicKeyPath != null && !publicKeyPath.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "publicKey and publicKeyPath cannot both be provided");
            return ResponseEntity.badRequest().body(error);
        }

        if (publicKey != null && !publicKey.isBlank() || publicKeyPath != null && !publicKeyPath.isBlank()) {
            publicKey = publicKey != null && !publicKey.isBlank()
                    ? publicKeyPayloadService.normalizePublicKeyText(publicKey)
                    : publicKeyPayloadService.readPublicKey(PathInputNormalizer.toPath(publicKeyPath));
            accountId = publicKeyPayloadService.accountIdForPublicKey(publicKey);
        } else {
            if (accountId == null || accountId.isBlank()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("success", false);
                error.put("message", "accountId or publicKey/publicKeyPath is required");
                return ResponseEntity.badRequest().body(error);
            }
            publicKey = searchPublicKeyForContact(accountId);
        }

        ContactRecord contact = localContactBookService.addContact(accountId, publicKey, alias);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("contactIndex", contact.getContactIndex());
        payload.put("alias", contact.getAlias());
        payload.put("accountId", contact.getAccountId());
        payload.put("publicKey", contact.getPublicKey());
        payload.put("message", "Contact added successfully");
        return ResponseEntity.ok(payload);
    }

    @PutMapping("/{contactIndex}")
    public ResponseEntity<Map<String, Object>> updateContact(
            @PathVariable int contactIndex,
            @RequestBody Map<String, String> request) {
        String alias = request.get("alias");
        String publicKey = request.get("publicKey");
        String publicKeyPath = request.get("publicKeyPath");

        if (publicKey != null && !publicKey.isBlank() && publicKeyPath != null && !publicKeyPath.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "publicKey and publicKeyPath cannot both be provided");
            return ResponseEntity.badRequest().body(error);
        }

        return localContactBookService.findContactByIndex(contactIndex)
                .map(existing -> {
                    String normalizedPublicKey = null;
                    String accountId = null;
                    if (publicKey != null && !publicKey.isBlank() || publicKeyPath != null && !publicKeyPath.isBlank()) {
                        normalizedPublicKey = publicKey != null && !publicKey.isBlank()
                                ? publicKeyPayloadService.normalizePublicKeyText(publicKey)
                                : publicKeyPayloadService.readPublicKey(PathInputNormalizer.toPath(publicKeyPath));
                        try {
                            accountId = publicKeyPayloadService.accountIdForPublicKey(normalizedPublicKey);
                        } catch (Exception ex) {
                            throw new IllegalStateException("Unable to calculate accountId from publicKey", ex);
                        }
                    }
                    ContactRecord updated = localContactBookService.updateContact(contactIndex, alias, normalizedPublicKey, accountId);

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("success", true);
                    payload.put("contactIndex", updated.getContactIndex());
                    payload.put("alias", updated.getAlias());
                    payload.put("accountId", updated.getAccountId());
                    payload.put("publicKey", updated.getPublicKey());
                    payload.put("message", "Contact updated successfully");
                    return ResponseEntity.ok(payload);
                })
                .orElseGet(() -> {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("message", "Contact not found");
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{contactIndex}")
    public ResponseEntity<Map<String, Object>> removeContact(@PathVariable int contactIndex) {
        try {
            localContactBookService.removeContactByIndex(contactIndex);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("contactIndex", contactIndex);
            payload.put("message", "Contact removed successfully");
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/blacklist")
    public ResponseEntity<List<Map<String, Object>>> listBlacklist() {
        List<BlacklistRecord> records = localContactBookService.listBlacklist();
        List<Map<String, Object>> result = records.stream()
                .map(record -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("accountId", record.getAccountId());
                    item.put("publicKey", record.getPublicKey());
                    item.put("reason", record.getReason());
                    item.put("createdAt", record.getCreatedAt());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/blacklist/{accountId}")
    public ResponseEntity<Map<String, Object>> getBlacklist(@PathVariable String accountId) {
        return localContactBookService.findBlacklist(accountId)
                .map(record -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("accountId", record.getAccountId());
                    item.put("publicKey", record.getPublicKey());
                    item.put("reason", record.getReason());
                    item.put("createdAt", record.getCreatedAt());
                    return ResponseEntity.ok(item);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/blacklist")
    public ResponseEntity<Map<String, Object>> addBlacklist(@RequestBody Map<String, String> request) {
        if (request == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "accountId is required");
            return ResponseEntity.badRequest().body(error);
        }
        String accountId = request.get("accountId");
        String reason = request.get("reason");
        String publicKey = request.get("publicKey");

        if (accountId == null || accountId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "accountId is required");
            return ResponseEntity.badRequest().body(error);
        }

        BlacklistRecord record = localContactBookService.addBlacklist(accountId, publicKey, reason);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("accountId", record.getAccountId());
        payload.put("reason", record.getReason());
        payload.put("message", "Added to blacklist");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/blacklist/contact/{contactIndex}")
    public ResponseEntity<Map<String, Object>> addBlacklistByContact(
            @PathVariable int contactIndex,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String reason = request != null ? request.get("reason") : null;
            BlacklistRecord record = localContactBookService.addBlacklistByContactIndex(contactIndex, reason);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("accountId", record.getAccountId());
            payload.put("reason", record.getReason());
            payload.put("message", "Contact added to blacklist");
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/blacklist/{accountId}")
    public ResponseEntity<Map<String, Object>> removeBlacklist(@PathVariable String accountId) {
        try {
            localContactBookService.removeBlacklist(accountId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("accountId", accountId);
            payload.put("message", "Removed from blacklist");
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/blacklist/check/{accountId}")
    public ResponseEntity<Map<String, Object>> checkBlacklist(@PathVariable String accountId) {
        boolean isBlacklisted = localContactBookService.isBlacklisted(accountId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("blacklisted", isBlacklisted);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/search-user/{accountId}")
    public ResponseEntity<Map<String, Object>> searchUser(@PathVariable String accountId) {
        OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(accountId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("found", result.isSearchResult());
        payload.put("accountId", result.getAccountId());
        payload.put("message", result.getMessage());
        if (result.isSearchResult()) {
            payload.put("publicKey", result.getPublicKey());
        }
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/search-user-add")
    public ResponseEntity<Map<String, Object>> searchUserAndAddContact(@RequestBody Map<String, String> request) {
        if (request == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "accountId is required");
            return ResponseEntity.badRequest().body(error);
        }
        String accountId = request.get("accountId");
        String alias = request.get("alias");

        if (accountId == null || accountId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "accountId is required");
            return ResponseEntity.badRequest().body(error);
        }

        OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(accountId);
        if (!result.isSearchResult()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "User not found online: " + result.getMessage());
            return ResponseEntity.ok(error);
        }

        ContactRecord contact = localContactBookService.addContact(result.getAccountId(), result.getPublicKey(), alias);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("contactIndex", contact.getContactIndex());
        payload.put("alias", contact.getAlias());
        payload.put("accountId", contact.getAccountId());
        payload.put("message", "Contact added successfully");
        return ResponseEntity.ok(payload);
    }

    private String searchPublicKeyForContact(String accountId) {
        try {
            OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(accountId);
            if (result.isSearchResult() && result.getPublicKey() != null && !result.getPublicKey().isBlank()) {
                return result.getPublicKey();
            }
        } catch (Exception ignored) {
            // Keep console behavior: save the contact even when the server cannot provide a public key.
        }
        return null;
    }
}
