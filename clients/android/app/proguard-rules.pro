-keep class com.server.smsforwarder.SmsReceiver { *; }
-keep class com.server.smsforwarder.BootReceiver { *; }
-keep class com.server.smsforwarder.DeviceStateReceiver { *; }
-keep class com.server.smsforwarder.ForwardWorker { *; }
-keep class com.server.smsforwarder.HeartbeatWorker { *; }

# JavaMail discovers SMTP providers through META-INF configuration files.
-keep class com.sun.mail.smtp.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
