<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>

	<xsl:template match="/">
	  <transformedData>
		<xsl:attribute name="id"><xsl:value-of select="data/@id" /></xsl:attribute>
		<transformedValue>
		    <xsl:attribute name="transformedAttr"><xsl:value-of select="data/value/@attr" /></xsl:attribute>
			<xsl:value-of select="data/value" />
		</transformedValue>
	  </transformedData>
	</xsl:template>
</xsl:stylesheet>
