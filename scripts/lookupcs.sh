#!/bin/bash
fccresp=$(curl -s https://data.fcc.gov/api/license-view/basicSearch/getLicenses?searchValue=$1 | xpath -q -e '//License' | grep Active)
#echo $fccresp
{
 licname=$(echo $fccresp | xpath -q -e '//licName/' | sed -e 's/<[^>]*>//g')
 statusdesc=$(echo $fccresp | xpath -q -e '//statusDesc/' | sed -e 's/<[^>]*>//g')
 servicedesc=$(echo $fccresp | xpath -q -e '//serviceDesc/' | sed -e 's/<[^>]*>//g')
 frn=$(echo $fccresp | xpath -q -e '//frn/' | sed -e 's/<[^>]*>//g')
 expires=$(echo $fccresp | xpath -q -e '//expiredDate/' | sed -e 's/<[^>]*>//g')

 echo "   Name: $licname"
 echo "Service: $servicedesc"
 echo "    FRN: $frn"
 echo "  State: $statusdesc"
 echo "Expires: $expires"
} 2> /dev/null
