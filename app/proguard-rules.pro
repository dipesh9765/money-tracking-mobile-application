# Room entities and generated code are kept by Room's own consumer rules; keep the entity
# package so reflection-free column names survive shrinking.
-keep class com.expenseinterceptor.data.local.*Entity { *; }

# Keep generic signatures and annotations Room relies on.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Apache POI pulls in optional desktop/XML libraries that do not exist on Android. They are only
# reached from code paths this app never uses (charts, signing, SVG, scripting).
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.stream.**
-dontwarn javax.swing.**
-dontwarn java.awt.**
-dontwarn aQute.bnd.annotation.**
-dontwarn de.rototor.pdfbox.**
-dontwarn net.sf.saxon.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontwarn org.osgi.**
-dontwarn org.slf4j.**

# Apache POI / XMLBeans load their OOXML schema classes and resources by reflection, so shrinking
# them can crash Excel import/export only in release builds. Keep them; the size cost is accepted.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
