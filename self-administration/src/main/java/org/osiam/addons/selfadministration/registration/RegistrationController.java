/*
 * Copyright (C) 2013 tarent AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.osiam.addons.selfadministration.registration;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.osiam.addons.selfadministration.plugin.api.Plugin;
import org.osiam.addons.selfadministration.plugin.api.PostRegistrationFailedException;
import org.osiam.addons.selfadministration.plugin.api.RegistrationFailedException;
import org.osiam.resources.scim.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/registration")
public class RegistrationController {

    private static final Logger LOGGER = Logger.getLogger(RegistrationController.class.getName());

    @Inject
    private Plugin plugin;

    @Inject
    private RegistrationService registrationService;

    @Inject
    private UserValidator userValidator;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(registrationService.getAllAllowedFields());
    }

    @RequestMapping(method = RequestMethod.GET)
    public String getRegistrationForm(final Model model) {
        model.addAttribute("registrationUser", new RegistrationUser());
        model.addAttribute("allowedFields", registrationService.getAllAllowedFields());
        return "registration";
    }

    @RequestMapping(method = RequestMethod.POST)
    public String register(@Valid @ModelAttribute final RegistrationUser registrationUser,
            final BindingResult bindingResult, final Model model, final HttpServletRequest request,
            final HttpServletResponse response) {

        userValidator.validate(registrationUser, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("allowedFields", registrationService.getAllAllowedFields());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "registration";
        }

        User user = registrationService.convertToScimUser(registrationUser);

        try {
            plugin.performPreRegistrationCheck(user);
        } catch (RegistrationFailedException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allowedFields", registrationService.getAllAllowedFields());

            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return "registration";
        }

        user = registrationService.saveRegistrationUser(user);

        registrationService.sendRegistrationEmail(user, request);

        model.addAttribute("user", user);

        response.setStatus(HttpStatus.CREATED.value());
        try {
            plugin.performPostRegistrationActions(user);
        } catch (PostRegistrationFailedException p) {
            LOGGER.log(
                    Level.WARNING,
                    "An exception occured while performing post registration actions for user with ID: " + user.getId(),
                    p);
        }
        return "registrationSuccess";
    }

    /**
     * Activates a previously registered user.
     * 
     * After activation E-Mail arrived the activation link will point to this URI.
     * 
     * @param userId
     *        the id of the registered user
     * @param activationToken
     *        the user's activation token, send by E-Mail
     * 
     * @return the registrationSuccess page
     */
    @RequestMapping(value = "/activation", method = RequestMethod.GET)
    public String confirmation(@RequestParam("userId") final String userId,
            @RequestParam("activationToken") final String activationToken, final Model model) {

        User user = registrationService.activateUser(userId, activationToken);

        model.addAttribute("user", user);

        return "activationSuccess";
    }
}