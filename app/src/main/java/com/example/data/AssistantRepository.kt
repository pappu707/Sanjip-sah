package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantRepository(private val dao: AssistantDao) {

    suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        dao.getUserByUsername(username)
    }

    suspend fun getUserById(userId: Int): User? = withContext(Dispatchers.IO) {
        dao.getUserById(userId)
    }

    fun getAllUsersFlow(): Flow<List<User>> = dao.getAllUsersFlow()

    suspend fun registerUser(username: String, passwordHash: String, isAdmin: Boolean = false): Long = withContext(Dispatchers.IO) {
        dao.insertUser(User(username = username, passwordHash = passwordHash, isAdmin = isAdmin))
    }

    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        dao.insertUser(user)
    }

    suspend fun deleteUser(userId: Int) = withContext(Dispatchers.IO) {
        dao.deleteUser(userId)
    }

    // --- Messages ---
    fun getMessagesForUser(userId: Int): Flow<List<ChatMessage>> = dao.getMessagesForUser(userId)

    fun getAllMessagesFlow(): Flow<List<ChatMessage>> = dao.getAllMessagesFlow()

    suspend fun insertMessage(userId: Int, senderName: String, role: String, text: String) = withContext(Dispatchers.IO) {
        dao.insertMessage(ChatMessage(userId = userId, senderName = senderName, role = role, text = text))
    }

    suspend fun deleteMessagesForUser(userId: Int) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForUser(userId)
    }

    suspend fun deleteMessage(messageId: Int) = withContext(Dispatchers.IO) {
        dao.deleteMessage(messageId)
    }

    // --- Settings ---
    fun getAllSettingsFlow(): Flow<List<SystemSetting>> = dao.getAllSettingsFlow()

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        dao.getSettingValue(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        dao.insertSetting(SystemSetting(key, value))
    }
}
