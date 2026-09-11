package com.p2p.meshify.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.crypto.CryptoModule
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.domain.repository.IChatRepository
import javax.inject.Singleton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke خفيف لـ [RepositoryModule] — يتحقّق أن DAO bindings و ChatRepository
 * يوفّران دون كسر الرسم البياني، وأن الـ scoping صحيح.
 *
 * ملاحظة: [dagger.hilt.InstallIn] retention هو CLASS (كما تم التحقّق بـ javap
 * على hilt-core-2.60.1.jar) لذا لا يمكن التحقّق منه بالـ reflection وقت التشغيل.
 * نكتفي بالتحقّق من [dagger.Module] (RUNTIME) + سلوك الـ providers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RepositoryModuleSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: MeshifyDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `RepositoryModule has dagger Module annotation`() {
        val ann = RepositoryModule::class.java.getAnnotation(dagger.Module::class.java)
        assertNotNull("RepositoryModule must have @Module", ann)
    }

    @Test
    fun `provideChatDao is singleton and delegates to database`() {
        val m = RepositoryModule::class.java.getDeclaredMethod(
            "provideChatDao", MeshifyDatabase::class.java
        )
        assertNotNull(m.getAnnotation(Singleton::class.java))
        val db = AppModule.provideDatabase(context).also { database = it }
        assertNotNull(RepositoryModule.provideChatDao(db))
    }

    @Test
    fun `provideMessageDao is singleton and non-null`() {
        val m = RepositoryModule::class.java.getDeclaredMethod(
            "provideMessageDao", MeshifyDatabase::class.java
        )
        assertNotNull(m.getAnnotation(Singleton::class.java))
        val db = AppModule.provideDatabase(context).also { database = it }
        assertNotNull(RepositoryModule.provideMessageDao(db))
    }

    @Test
    fun `providePendingMessageDao is singleton and non-null`() {
        val m = RepositoryModule::class.java.getDeclaredMethod(
            "providePendingMessageDao", MeshifyDatabase::class.java
        )
        assertNotNull(m.getAnnotation(Singleton::class.java))
        val db = AppModule.provideDatabase(context).also { database = it }
        assertNotNull(RepositoryModule.providePendingMessageDao(db))
    }

    @Test
    fun `provideChatRepository is singleton and provideChatRepositoryAsInterface returns same instance`() {
        val mRepo = RepositoryModule::class.java.getDeclaredMethod(
            "provideChatRepository",
            Context::class.java,
            com.p2p.meshify.core.common.util.StringResourceProvider::class.java,
            MeshifyDatabase::class.java,
            com.p2p.meshify.core.data.local.dao.ChatDao::class.java,
            com.p2p.meshify.core.data.local.dao.MessageDao::class.java,
            com.p2p.meshify.core.data.local.dao.PendingMessageDao::class.java,
            com.p2p.meshify.core.network.TransportManager::class.java,
            com.p2p.meshify.domain.repository.IFileManager::class.java,
            com.p2p.meshify.core.util.NotificationHelper::class.java,
            com.p2p.meshify.domain.repository.ISettingsRepository::class.java,
            com.p2p.meshify.core.crypto.MessageCipher::class.java
        )
        assertNotNull(mRepo.getAnnotation(Singleton::class.java))

        val mIface = RepositoryModule::class.java.getDeclaredMethod(
            "provideChatRepositoryAsInterface",
            com.p2p.meshify.core.data.repository.ChatRepositoryImpl::class.java
        )
        assertNotNull(mIface.getAnnotation(Singleton::class.java))

        val db = AppModule.provideDatabase(context).also { database = it }
        val chatDao = RepositoryModule.provideChatDao(db)
        val messageDao = RepositoryModule.provideMessageDao(db)
        val pendingDao = RepositoryModule.providePendingMessageDao(db)

        val stringProvider = AppModule.provideStringResourceProvider(context)
        val settings = AppModule.provideSettingsRepository(context)
        val peerIds = AppModule.providePeerIdProvider(context)
        val keyManager = CryptoModule.provideDeviceKeysetManager(context)
        val store = CryptoModule.providePeerPublicKeyStore()
        val cipher = CryptoModule.provideMessageCipher(keyManager, store)
        val transportManager = AppModule.provideTransportManager(context, settings, peerIds, cipher, store)
        val fileManager = AppModule.provideFileManager(context)
        val notifications = AppModule.provideNotificationHelper(context)

        val repo = RepositoryModule.provideChatRepository(
            context = context,
            stringProvider = stringProvider,
            database = db,
            chatDao = chatDao,
            messageDao = messageDao,
            pendingMessageDao = pendingDao,
            transportManager = transportManager,
            fileManager = fileManager,
            notificationHelper = notifications,
            settingsRepository = settings,
            messageCipher = cipher
        )
        assertNotNull(repo)

        val iface: IChatRepository = RepositoryModule.provideChatRepositoryAsInterface(repo)
        assertNotNull(iface)
        assertSame("IChatRepository binding must be the same singleton instance", repo, iface)
    }
}
