# Test image only containing the generated .htaccess file with redirects
FROM httpd:2.4

# It's the default file except we AllowOverride All, and enable mod_rewrite
COPY ./httpd.conf /usr/local/apache2/conf/httpd.conf
COPY ./htaccess.tmp /usr/local/apache2/htdocs/.htaccess

RUN echo "{}" > /usr/local/apache2/htdocs/uctest.json
RUN chmod a+r /usr/local/apache2/htdocs/.htaccess
