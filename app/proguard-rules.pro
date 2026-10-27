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
