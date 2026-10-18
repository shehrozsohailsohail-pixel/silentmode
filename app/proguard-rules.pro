# Room and Hilt (KSP-based, not kapt/reflection-based) ship their own
# consumer ProGuard rules inside their AARs, so this file is intentionally
# close to empty - unlike the older kapt/reflection era, manual keep rules
# for @Entity/@Dao/@Module classes are not required here.
#
# Add app-specific keep rules below only when release-build testing (see
# README "What hasn't been verified") actually surfaces a stripped class or
# method - don't add speculative rules for classes that turn out fine.
